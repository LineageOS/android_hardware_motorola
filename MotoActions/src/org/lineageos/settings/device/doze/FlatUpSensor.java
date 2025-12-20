/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

public class FlatUpSensor implements ScreenStateNotifier {
    private static final String TAG = "MotoActions-FlatUpSensor";

    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final DozePulseAction mDozePulseAction;
    private final Sensor mFlatUpSensor;
    private final Sensor mStowSensor;

    private boolean mEnabled;
    private boolean mIsStowed;
    private boolean mLastFlatUp;

    public FlatUpSensor(MotoActionsSettings MotoActionsSettings, Context context,
                        SensorHelper sensorHelper) {
        mMotoActionsSettings = MotoActionsSettings;
        mSensorHelper = sensorHelper;
        mDozePulseAction = new DozePulseAction(context);

        mFlatUpSensor = sensorHelper.getFlatUpSensor();
        mStowSensor = sensorHelper.getStowSensor();
    }

    @Override
    public void screenTurnedOn() {
        if (mEnabled) {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(mFlatUpListener);
            mSensorHelper.unregisterListener(mStowListener);
            mEnabled = false;
        }
        mDozePulseAction.onStateChanged(false);
    }

    @Override
    public void screenTurnedOff() {
        if (mMotoActionsSettings.isPickUpEnabled() && !mEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mFlatUpSensor, mFlatUpListener);
            mSensorHelper.registerListener(mStowSensor, mStowListener);
            mEnabled = true;
        }
        mDozePulseAction.onStateChanged(true);
    }

    private final SensorEventListener mFlatUpListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            boolean thisFlatUp = (event.values[0] != 0);

            Log.d(TAG, "event: " + thisFlatUp + " mLastFlatUp=" + mLastFlatUp + " mIsStowed=" +
                    mIsStowed);

            mDozePulseAction.onStartPulse(mLastFlatUp && !thisFlatUp && !mIsStowed);
            mLastFlatUp = thisFlatUp;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final SensorEventListener mStowListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            mIsStowed = (event.values[0] != 0);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
