/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.lineageos.settings.device.actions.ChopChopSensor;
import org.lineageos.settings.device.actions.FlipToMute;
import org.lineageos.settings.device.actions.LiftToSilence;
import org.lineageos.settings.device.actions.ProximitySilencer;
import org.lineageos.settings.device.actions.UpdatedStateNotifier;
import org.lineageos.settings.device.doze.DozePulseAction;
import org.lineageos.settings.device.doze.FlatUpSensor;
import org.lineageos.settings.device.doze.ScreenStateNotifier;
import org.lineageos.settings.device.doze.StowSensor;

import java.util.LinkedList;
import java.util.List;

public class MotoActionsService extends Service implements ScreenStateNotifier,
        UpdatedStateNotifier {
    private static final String TAG = "MotoActions";

    private final List<ScreenStateNotifier> mScreenStateNotifiers = new LinkedList<>();
    private final List<UpdatedStateNotifier> mUpdatedStateNotifiers = new LinkedList<>();

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    public void onCreate() {
        Log.d(TAG, "Starting");

        MotoActionsSettings actionsSettings = new MotoActionsSettings(this, this);
        SensorHelper sensorHelper = new SensorHelper(this);

        // Actionable sensors get screen on/off notifications
        mScreenStateNotifiers.add(new StowSensor(actionsSettings, this, sensorHelper));
        mScreenStateNotifiers.add(new FlatUpSensor(actionsSettings, this, sensorHelper));

        // Other actions that are always enabled
        mUpdatedStateNotifiers.add(new ChopChopSensor(actionsSettings, this, sensorHelper));
        mUpdatedStateNotifiers.add(new ProximitySilencer(actionsSettings, this, sensorHelper));
        mUpdatedStateNotifiers.add(new FlipToMute(actionsSettings, this, sensorHelper));
        mUpdatedStateNotifiers.add(new LiftToSilence(actionsSettings, this, sensorHelper));

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                TAG + ":WakeLock");

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, filter);

        updateState();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void screenTurnedOn() {
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOn();
        }
    }

    @Override
    public void screenTurnedOff() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOff();
        }
    }

    public void updateState() {
        if (mPowerManager.isInteractive()) {
            screenTurnedOn();
        } else {
            screenTurnedOff();
        }
        for (UpdatedStateNotifier notifier : mUpdatedStateNotifiers) {
            notifier.updateState();
        }
    }

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                screenTurnedOff();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                screenTurnedOn();
            }
        }
    };
}
