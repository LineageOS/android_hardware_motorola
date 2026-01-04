/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.UserHandle;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

public class DozePulse {
    private static final String TAG = "MotoActions-DozePulse";

    // Maximum time for the hand to cover the sensor: 1s
    private static final long HANDWAVE_MAX_DELTA_NS = 1000L * 1000 * 1000;

    // Minimum time until the device is considered to have been in the pocket: 5s
    private static final long POCKET_MIN_DELTA_NS = 5000L * 1000 * 1000;

    // Minimum time between doze pulses: 1.5s
    private static final int DELAY_BETWEEN_DOZES_IN_MS = 1500;

    private final Context mContext;
    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final Sensor mFlatUpSensor;
    private final Sensor mStowSensor;

    private boolean mEnabled;
    private boolean mIsFlatUp;
    private boolean mLastFlatUp;
    private boolean mIsStowed;
    private boolean mLastStowed;
    private long mLastStowedTime;
    private long mLastDoze;

    public DozePulse(MotoActionsSettings MotoActionsSettings, Context context,
                        SensorHelper sensorHelper) {
        mContext = context;
        mMotoActionsSettings = MotoActionsSettings;
        mSensorHelper = sensorHelper;

        mFlatUpSensor = sensorHelper.getFlatUpSensor();
        mStowSensor = sensorHelper.getStowSensor();
    }

    public void screenTurnedOn() {
        if (mEnabled) {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(mFlatUpListener);
            mSensorHelper.unregisterListener(mStowListener);
            mEnabled = false;
        }
    }

    public void screenTurnedOff() {
        if (!mEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mFlatUpSensor, mFlatUpListener);
            mSensorHelper.registerListener(mStowSensor, mStowListener);
            mEnabled = true;
        }
        mLastDoze = System.currentTimeMillis();
    }

    private final SensorEventListener mFlatUpListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            mIsFlatUp = (event.values[0] != 0);

            Log.d(TAG, "event: " + mIsFlatUp + " mLastFlatUp=" + mLastFlatUp + " mIsStowed=" +
                    mIsStowed);

            boolean canPulse = mMotoActionsSettings.isPickUpEnabled()
                    && (mLastFlatUp && !mIsFlatUp && !mIsStowed);
            sendDozePulse(canPulse);
            mLastFlatUp = mIsFlatUp;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final SensorEventListener mStowListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            mIsStowed = (event.values[0] != 0);
            if (mIsStowed) {
                mLastStowedTime = event.timestamp;
            } else if (mLastStowed) {
                sendDozePulse(shouldPulse(event.timestamp));
            }
            mLastStowed = mIsStowed;
            Log.d(TAG, "event: " + mIsStowed);
        }

        private boolean shouldPulse(long timestamp) {
            long delta = timestamp - mLastStowedTime;

            boolean irWakeupEnabled = mMotoActionsSettings.isIrWakeupEnabled();
            boolean pocketGestureEnabled = mMotoActionsSettings.isPocketGestureEnabled();

           if (irWakeupEnabled && pocketGestureEnabled) {
                return true;
            } else if (irWakeupEnabled) {
                return delta < HANDWAVE_MAX_DELTA_NS;
            } else if (pocketGestureEnabled) {
                return delta >= POCKET_MIN_DELTA_NS;
            }
            return false;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public void sendDozePulse(boolean canPulse) {
        if (!canPulse) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - mLastDoze > DELAY_BETWEEN_DOZES_IN_MS) {
            mLastDoze = now;
            Log.d(TAG, "Launching doze pulse");
            Intent pulseIntent = new Intent("com.android.systemui.doze.pulse");
            mContext.sendBroadcastAsUser(pulseIntent, UserHandle.CURRENT);
        }
    }
}
