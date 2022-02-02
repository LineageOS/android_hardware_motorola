/*
 * Copyright (c) 2015 The CyanogenMod Project
 * Copyright (c) 2017-2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device.doze;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorAction;
import org.lineageos.settings.device.SensorHelper;

public class StowSensor implements ScreenStateNotifier, SensorEventListener {
    private static final String TAG = "MotoActions-StowSensor";

    // Maximum time for the hand to cover the sensor: 1s
    private static final long HANDWAVE_MAX_DELTA_NS = 1000L * 1000 * 1000;

    // Minimum time until the device is considered to have been in the pocket: 5s
    private static final long POCKET_MIN_DELTA_NS = 5000L * 1000 * 1000;

    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final SensorAction mSensorAction;
    private final Sensor mSensor;

    private boolean mEnabled;
    private boolean mLastStowed;
    private long mLastStowedTime;

    public StowSensor(MotoActionsSettings MotoActionsSettings, SensorHelper sensorHelper,
                      SensorAction action) {
        mMotoActionsSettings = MotoActionsSettings;
        mSensorHelper = sensorHelper;
        mSensorAction = action;

        mSensor = sensorHelper.getStowSensor();
    }

    @Override
    public void screenTurnedOn() {
        if (mEnabled) {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(this);
            mEnabled = false;
        }
    }

    @Override
    public void screenTurnedOff() {
        if ((mMotoActionsSettings.isPocketGestureEnabled()
                || mMotoActionsSettings.isIrWakeupEnabled())
                && !mEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mSensor, this);
            mEnabled = true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean thisStowed = (event.values[0] != 0);
        if (thisStowed) {
            mLastStowedTime = event.timestamp;
        } else if (mLastStowed) {
            if (shouldPulse(event.timestamp)) {
                mSensorAction.action();
            }
        }
        mLastStowed = thisStowed;
        Log.d(TAG, "event: " + thisStowed);
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
}
