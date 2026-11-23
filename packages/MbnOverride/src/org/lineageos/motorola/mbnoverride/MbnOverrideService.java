/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.mbnoverride;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.qualcomm.qcrilhook.QcRilHook;
import com.qualcomm.qcrilhook.QcRilHookCallback;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MbnOverrideService extends Service {
    private static final String T = "MbnOverride";
    private static final String ACT_PDC = "qualcomm.intent.action.ACTION_PDC_DATA_RECEIVED";
    private static final String ACT_LIST = "qualcomm.intent.action.ACTION_PDC_CONFIG_LIST_RECEIVED";

    // Apply MBN configuration per slot using the slot bitmask.

    private static final int MAX_APPLIES = 3;                 // per slot, per window
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int PDC_READY_TIMEOUT_S = 180;

    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
    private final Object rxLock = new Object();
    private volatile int rxCount = 0;
    private volatile int lastErr = -1;
    private volatile int lastSub = -1;
    private volatile boolean applying = false;
    private final Set<String> ids = Collections.synchronizedSet(new LinkedHashSet<String>());
    private final Map<String, String> nameToId = new HashMap<>();             // ex thread only
    private final Map<Integer, ArrayDeque<Long>> applies = new HashMap<>();   // ex thread only
    private volatile QcRilHook hook;
    private volatile boolean ready;
    private ScheduledFuture<?> pending;
    private List<String[]> overrides = new ArrayList<>();

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            Bundle e = i.getExtras();
            if (e == null) return;
            if (ACT_LIST.equals(a)) {
                byte[] d = e.getByteArray("list_data");
                if (d != null) parseList(d);
            } else if (ACT_PDC.equals(a)) {
                Object er = e.get("error");
                lastErr = (er instanceof Number) ? ((Number) er).intValue() : -1;
                Object sb = e.get("sub_id");
                lastSub = (sb instanceof Number) ? ((Number) sb).intValue() : -1;
                rxCount++;
                synchronized (rxLock) { rxLock.notifyAll(); }
            }
        }
    };

    private final SubscriptionManager.OnSubscriptionsChangedListener subL =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() { scheduleDebounced(5); }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (hook != null) return START_STICKY;
        if (!getResources().getBoolean(R.bool.config_mbnOverrideEnabled)) {
            Log.i(T, "disabled by config");
            stopSelf();
            return START_NOT_STICKY;
        }
        overrides = loadOverrides();
        IntentFilter f = new IntentFilter();
        f.addAction(ACT_PDC);
        f.addAction(ACT_LIST);
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED);
        getSystemService(SubscriptionManager.class)
                .addOnSubscriptionsChangedListener(getMainExecutor(), subL);
        hook = new QcRilHook(getApplicationContext(), new QcRilHookCallback() {
            @Override
            public void onQcRilHookReady() {
                ready = true;
                scheduleDebounced(1);   // also runs again after a qcril restart
            }
            @Override
            public void onQcRilHookDisconnected() { ready = false; }
        });
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(rx); } catch (Exception ignored) {}
        getSystemService(SubscriptionManager.class).removeOnSubscriptionsChangedListener(subL);
        ex.shutdownNow();
        super.onDestroy();
    }

    private synchronized void scheduleDebounced(int sec) {
        if (pending != null) pending.cancel(false);
        pending = ex.schedule(this::reconcile, sec, TimeUnit.SECONDS);
    }

    private List<String[]> loadOverrides() {
        List<String[]> out = new ArrayList<>();
        try (XmlResourceParser p = getResources().getXml(R.xml.mbn_overrides)) {
            for (int ev = p.getEventType(); ev != XmlResourceParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlResourceParser.START_TAG && "override".equals(p.getName())) {
                    out.add(new String[] {
                        p.getAttributeValue(null, "mcc"),
                        p.getAttributeValue(null, "mnc"),
                        p.getAttributeValue(null, "name") });
                }
            }
        } catch (Exception e) {
            Log.e(T, "bad mbn_overrides.xml", e);
        }
        return out;
    }

    private String lookup(String op) {
        if (op == null || op.length() < 5) return null;
        String mcc = op.substring(0, 3), mnc = op.substring(3);
        for (String[] o : overrides) {
            if (!mcc.equals(o[0])) continue;
            if (o[1] == null || mnc.equals(o[1])) return o[2];
        }
        return null;
    }

    // list_data: [status][more][count] then count x ([len=20][20 bytes id])
    private void parseList(byte[] d) {
        int n = d.length > 2 ? (d[2] & 0xff) : 0;
        int p = 3;
        for (int k = 0; k < n && p < d.length; k++) {
            int len = d[p++] & 0xff;
            if (len != 20 || p + len > d.length) break;
            ids.add(new String(d, p, len, StandardCharsets.ISO_8859_1));
            p += len;
        }
    }

    private boolean waitPdcReady() throws InterruptedException {
        for (int t = 0; t < PDC_READY_TIMEOUT_S; t += 5) {
            String c = hook.qcRilGetConfig(0);
            if (c != null && !c.isEmpty()) return true;
            Thread.sleep(5000);
        }
        return false;
    }

    private void refreshNames() throws InterruptedException {
        ids.clear();
        nameToId.clear();
        hook.qcRilGetAllConfigs();
        int last = -1, stable = 0;
        for (int t = 0; t < 10000 && stable < 3; t += 500) {
            Thread.sleep(500);
            int n = ids.size();
            if (n > 0 && n == last) stable++; else stable = 0;
            last = n;
        }
        for (String id : new ArrayList<>(ids)) {
            String m = hook.qcRilGetMetaInfoForConfig(id, 0);
            if (m != null) nameToId.putIfAbsent(m, id);
        }
        Log.i(T, "modem configs=" + ids.size() + " named=" + nameToId.size());

        if (Log.isLoggable(T, Log.DEBUG)) {
            List<String> names = new ArrayList<>(nameToId.keySet());
            Collections.sort(names);

            for (String name : names) {
                Log.d(T, "config: " + name);
            }
        }
    }

    private boolean allowApply(int slot) {
        long now = SystemClock.elapsedRealtime();
        ArrayDeque<Long> q = applies.computeIfAbsent(slot, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
        if (q.size() >= MAX_APPLIES) return false;
        q.addLast(now);
        return true;
    }

    private boolean waitRx(int before, long ms) throws InterruptedException {
        long end = SystemClock.elapsedRealtime() + ms;
        synchronized (rxLock) {
            while (rxCount <= before) {
                long left = end - SystemClock.elapsedRealtime();
                if (left <= 0) return false;
                rxLock.wait(left);
            }
        }
        return true;
    }

    private void reconcile() {
        try {
            if (!ready) return;
            TelephonyManager tm = getSystemService(TelephonyManager.class);
            if (tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                Log.i(T, "call in progress, retry later");
                scheduleDebounced(60);
                return;
            }
            List<SubscriptionInfo> subs =
                    getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if (subs == null || subs.isEmpty()) return;

            Map<Integer, String> want = new HashMap<>();   // slot -> profile name
            for (SubscriptionInfo si : subs) {
                int slot = si.getSimSlotIndex();
                String op = tm.createForSubscriptionId(si.getSubscriptionId()).getSimOperator();
                String name = lookup(op);
                Log.i(T, "slot=" + slot + " sim=" + op + " -> " + (name == null ? "(stock)" : name));
                if (name != null) want.put(slot, name);
            }
            if (want.isEmpty()) {
                if (Log.isLoggable(T, Log.DEBUG) && nameToId.isEmpty() && waitPdcReady()) {
                    refreshNames();
                }
                return;
            }

            if (!waitPdcReady()) { Log.w(T, "PDC never became ready"); return; }
            if (nameToId.isEmpty()) refreshNames();

            for (Map.Entry<Integer, String> en : want.entrySet()) {
                int slot = en.getKey();
                String name = en.getValue();
                String id = nameToId.get(name);
                if (id == null) {
                    refreshNames();
                    id = nameToId.get(name);
                }
                if (id == null) { Log.w(T, name + " not present in modem"); continue; }
                String cur = hook.qcRilGetConfig(slot);
                if (id.equals(cur)) { Log.i(T, "slot " + slot + " already on " + name); continue; }
                if (!allowApply(slot)) { Log.w(T, "rate limit for slot " + slot); continue; }
                apply(slot, id, name);
            }
        } catch (Throwable t) {
            Log.e(T, "reconcile failed", t);
        }
    }

    private void apply(int slot, String id, String name) throws InterruptedException {
        int mask = 1 << slot;
        applying = true;
        try {
            int n0 = rxCount;
            boolean sel = hook.qcRilSelectConfig(id, mask);
            if (!sel || !waitRx(n0, 8000) || lastErr != 0) {
                Log.e(T, "slot " + slot + " select failed sel=" + sel + " err=" + lastErr);
                return;
            }
            Log.i(T, "slot " + slot + " select ok (rx sub=" + lastSub + ")");
            boolean act = hook.qcRilActivateConfig(slot);
            Log.i(T, "slot " + slot + " activate=" + act);
            for (int i = 0; i < 12; i++) {
                Thread.sleep(5000);
                if (id.equals(hook.qcRilGetConfig(slot))) {
                    Log.i(T, "slot " + slot + " now active: " + name);
                    return;
                }
            }
            Log.e(T, "slot " + slot + " activation of " + name + " not confirmed");
        } finally {
            applying = false;
        }
    }
}
