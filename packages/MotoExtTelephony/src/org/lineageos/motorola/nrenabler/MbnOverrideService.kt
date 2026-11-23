/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.XmlResourceParser
import android.os.IBinder
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.qualcomm.qcrilhook.QcRilHook
import com.qualcomm.qcrilhook.QcRilHookCallback
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Selects a modem carrier config (MBN) for every SIM slot whose SIM operator is listed in
 * res/xml/mbn_overrides.xml. SIMs without an entry are left alone. See README.md.
 */
class MbnOverrideService : Service() {

    private val ex = Executors.newSingleThreadScheduledExecutor()
    private val rxLock = Object()

    @Volatile private var rxCount = 0

    @Volatile private var lastErr = -1

    private val ids: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())
    private val nameToId = HashMap<String, String>() // ex thread only
    private val applies = HashMap<Int, ArrayDeque<Long>>() // ex thread only

    @Volatile private var hook: QcRilHook? = null

    @Volatile private var ready = false

    private var pending: ScheduledFuture<*>? = null
    private var overrides: List<Array<String?>> = ArrayList()

    private val rx =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val a = i.action
                val e = i.extras ?: return
                if (ACT_LIST == a) {
                    val d = e.getByteArray("list_data")
                    if (d != null) parseList(d)
                } else if (ACT_PDC == a) {
                    @Suppress("DEPRECATION") val er = e.get("error")
                    lastErr = if (er is Number) er.toInt() else -1
                    rxCount++
                    synchronized(rxLock) { rxLock.notifyAll() }
                }
            }
        }

    private val subL =
        object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                scheduleDebounced(5)
            }
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (hook != null) return START_STICKY
        if (!resources.getBoolean(R.bool.config_mbnOverrideEnabled)) {
            Log.i(T, "disabled by config")
            stopSelf()
            return START_NOT_STICKY
        }
        overrides = loadOverrides()
        val f =
            IntentFilter().apply {
                addAction(ACT_PDC)
                addAction(ACT_LIST)
            }
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED)
        getSystemService(SubscriptionManager::class.java)
            .addOnSubscriptionsChangedListener(mainExecutor, subL)
        hook =
            QcRilHook(
                applicationContext,
                object : QcRilHookCallback {
                    override fun onQcRilHookReady() {
                        ready = true
                        scheduleDebounced(1) // also runs again after a qcril restart
                    }

                    override fun onQcRilHookDisconnected() {
                        ready = false
                    }
                },
            )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(rx)
        } catch (_: Exception) {}
        getSystemService(SubscriptionManager::class.java).removeOnSubscriptionsChangedListener(subL)
        ex.shutdownNow()
        super.onDestroy()
    }

    @Synchronized
    private fun scheduleDebounced(sec: Int) {
        if (ex.isShutdown) return
        pending?.cancel(false)
        try {
            pending = ex.schedule({ reconcile() }, sec.toLong(), TimeUnit.SECONDS)
        } catch (e: RejectedExecutionException) {
            Log.w(T, "service is shutting down")
        }
    }

    // Entries: { mcc, mnc (may be null), name, slot (may be null) }
    private fun loadOverrides(): List<Array<String?>> {
        val out = ArrayList<Array<String?>>()
        try {
            resources.getXml(R.xml.mbn_overrides).use { p ->
                var ev = p.eventType
                while (ev != XmlResourceParser.END_DOCUMENT) {
                    if (ev == XmlResourceParser.START_TAG && "override" == p.name) {
                        out.add(
                            arrayOf(
                                p.getAttributeValue(null, "mcc"),
                                p.getAttributeValue(null, "mnc"),
                                p.getAttributeValue(null, "name"),
                                p.getAttributeValue(null, "slot"),
                            )
                        )
                    }
                    ev = p.next()
                }
            }
        } catch (e: Exception) {
            Log.e(T, "bad mbn_overrides.xml", e)
        }
        return out
    }

    /** Returns the config name for a SIM operator in a slot; an entry with a slot wins. */
    private fun lookup(op: String?, slot: Int): String? {
        if (op == null || op.length < 5) return null
        val mcc = op.substring(0, 3)
        val mnc = op.substring(3)
        var fallback: String? = null
        for (o in overrides) {
            if (mcc != o[0]) continue
            if (o[1] != null && mnc != o[1]) continue
            if (o[3] != null) {
                if (slot.toString() == o[3]) return o[2]
            } else if (fallback == null) {
                fallback = o[2]
            }
        }
        return fallback
    }

    // list_data: [status][more][count] then count x ([len=20][20 bytes id])
    private fun parseList(d: ByteArray) {
        val n = if (d.size > 2) (d[2].toInt() and 0xff) else 0
        var p = 3
        for (k in 0 until n) {
            if (p >= d.size) break
            val len = d[p++].toInt() and 0xff
            if (len != 20 || p + len > d.size) break
            ids.add(String(d, p, len, StandardCharsets.ISO_8859_1))
            p += len
        }
    }

    @Throws(InterruptedException::class)
    private fun waitPdcReady(): Boolean {
        val h = hook ?: return false
        var t = 0
        while (t < PDC_READY_TIMEOUT_S) {
            val c = h.qcRilGetConfig(0)
            if (!c.isNullOrEmpty()) return true
            Thread.sleep(5000)
            t += 5
        }
        return false
    }

    @Throws(InterruptedException::class)
    private fun refreshNames() {
        val h = hook ?: return
        ids.clear()
        nameToId.clear()
        h.qcRilGetAllConfigs()
        var last = -1
        var stable = 0
        var t = 0
        while (t < 10000 && stable < 3) {
            Thread.sleep(500)
            val n = ids.size
            if (n > 0 && n == last) stable++ else stable = 0
            last = n
            t += 500
        }
        for (id in ArrayList(ids)) {
            val m = h.qcRilGetMetaInfoForConfig(id, 0)
            if (m != null) nameToId.putIfAbsent(m, id)
        }
        Log.i(T, "modem configs=${ids.size} named=${nameToId.size}")

        if (Log.isLoggable(T, Log.DEBUG)) {
            val names = ArrayList(nameToId.keys)
            names.sort()
            for (name in names) {
                Log.d(T, "config: $name")
            }
        }
    }

    private fun allowApply(slot: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        val q = applies.computeIfAbsent(slot) { ArrayDeque() }
        while (q.isNotEmpty()) {
            val first = q.firstOrNull() ?: break
            if (now - first <= WINDOW_MS) break
            q.pollFirst()
        }
        if (q.size >= MAX_APPLIES) return false
        q.addLast(now)
        return true
    }

    @Throws(InterruptedException::class)
    private fun waitRx(before: Int, ms: Long): Boolean {
        val end = SystemClock.elapsedRealtime() + ms
        synchronized(rxLock) {
            while (rxCount <= before) {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) return false
                rxLock.wait(left)
            }
        }
        return true
    }

    private fun reconcile() {
        try {
            val h = hook
            if (!ready || h == null) return
            val tm = getSystemService(TelephonyManager::class.java)
            @Suppress("DEPRECATION") val callState = tm.callState
            if (callState != TelephonyManager.CALL_STATE_IDLE) {
                Log.i(T, "call in progress, retry later")
                scheduleDebounced(60)
                return
            }
            val subs = getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
            if (subs.isNullOrEmpty()) return

            val want = HashMap<Int, String>() // slot -> config name
            for (si in subs) {
                val slot = si.simSlotIndex
                val op = tm.createForSubscriptionId(si.subscriptionId).simOperator
                val name = lookup(op, slot)
                Log.i(T, "slot=$slot sim=$op -> ${name ?: "(stock)"}")
                if (name != null) want[slot] = name
            }
            if (want.isEmpty()) {
                if (Log.isLoggable(T, Log.DEBUG) && nameToId.isEmpty() && waitPdcReady()) {
                    refreshNames()
                }
                return
            }

            if (!waitPdcReady()) {
                Log.w(T, "PDC never became ready")
                return
            }
            if (nameToId.isEmpty()) refreshNames()

            for ((slot, name) in want) {
                var id = nameToId[name]
                if (id == null) {
                    refreshNames()
                    id = nameToId[name]
                }
                if (id == null) {
                    Log.w(T, "$name not present in modem")
                    continue
                }
                if (id == h.qcRilGetConfig(slot)) {
                    Log.i(T, "slot $slot already on $name")
                    continue
                }
                if (!allowApply(slot)) {
                    Log.w(T, "rate limit for slot $slot")
                    continue
                }
                apply(slot, id, name, h)
            }
        } catch (t: Throwable) {
            Log.e(T, "reconcile failed", t)
        }
    }

    /*
     * QcRilHook addresses PDC requests to the primary RIL instance; qcril rejects them on the
     * other one ("QMI PDC client is only available on primary subscription"). The slot is
     * therefore part of the request: select takes the slot bit, activate and get take the slot
     * index. This is the combination tested on rtwo with one and two configs on the two slots;
     * the meaning of these arguments is not documented.
     */
    @Throws(InterruptedException::class)
    private fun apply(slot: Int, id: String, name: String, h: QcRilHook) {
        val n0 = rxCount
        lastErr = -1
        val sel = h.qcRilSelectConfig(id, 1 shl slot)
        if (!sel || !waitRx(n0, 8000) || lastErr != 0) {
            Log.e(T, "slot $slot select failed sel=$sel err=$lastErr")
            return
        }
        Log.i(T, "slot $slot select ok")
        Log.i(T, "slot $slot activate=${h.qcRilActivateConfig(slot)}")
        for (i in 0 until ACTIVATE_CHECKS) {
            Thread.sleep(5000)
            if (id == h.qcRilGetConfig(slot)) {
                Log.i(T, "slot $slot now active: $name")
                return
            }
        }
        Log.e(T, "slot $slot activation of " + name + " not confirmed")
    }

    companion object {
        private const val T = "MbnOverride"
        private const val ACT_PDC = "qualcomm.intent.action.ACTION_PDC_DATA_RECEIVED"
        private const val ACT_LIST = "qualcomm.intent.action.ACTION_PDC_CONFIG_LIST_RECEIVED"

        private const val MAX_APPLIES = 3 // per slot, per window
        private const val WINDOW_MS = 10 * 60 * 1000L
        private const val PDC_READY_TIMEOUT_S = 180
        private const val ACTIVATE_CHECKS = 12 // every 5 seconds
    }
}
