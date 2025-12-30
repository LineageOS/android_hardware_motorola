/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

public class ChopChopSensor implements UpdatedStateNotifier {
    private static final String TAG = "MotoActions-ChopChopSensor";

    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final Sensor mChopChopSensor;
    private final Sensor mProximitySensor;

    private boolean mIsEnabled;
    private boolean mProxIsCovered;

    public ChopChopSensor(MotoActionsSettings motoActionsSettings, SensorHelper sensorHelper) {
        mMotoActionsSettings = motoActionsSettings;
        mSensorHelper = sensorHelper;
        mChopChopSensor = sensorHelper.getChopChopSensor();
        mProximitySensor = sensorHelper.getProximitySensor();
    }

    @Override
    public synchronized void updateState() {
        if (mMotoActionsSettings.isChopChopGestureEnabled() && !mIsEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mChopChopSensor, mChopChopListener);
            mSensorHelper.registerListener(mProximitySensor, mProximityListener);
            mIsEnabled = true;
        } else if (!mMotoActionsSettings.isChopChopGestureEnabled() && mIsEnabled) {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(mChopChopListener);
            mSensorHelper.unregisterListener(mProximityListener);
            mIsEnabled = false;
        }
    }

    private final SensorEventListener mChopChopListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "chop chop triggered");
            if (mProxIsCovered) {
                Log.d(TAG, "proximity sensor covered, ignoring chop-chop");
                return;
            }
            mMotoActionsSettings.chopChopAction();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final SensorEventListener mProximityListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            float maxRange = Math.round(mProximitySensor.getMaximumRange() * 10f) / 10f;
            mProxIsCovered = event.values[0] < maxRange;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
