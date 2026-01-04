/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

public class ChopForTorch implements UpdatedStateNotifier {
    private static final String TAG = "MotoActions-ChopForTorch";

    private final CameraManager mCameraManager;
    private final Vibrator mVibrator;
    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final Sensor mChopChopSensor;
    private final Sensor mProximitySensor;

    private String mRearCameraId;
    private boolean mIsEnabled;
    private boolean mProxIsCovered;
    private boolean mTorchEnabled;

    private CameraManager.TorchCallback mTorchCallback =
            new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearCameraId)) {
                return;
            }
            mTorchEnabled = enabled;
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearCameraId)) {
                return;
            }
            mTorchEnabled = false;
        }
    };

    public ChopForTorch(MotoActionsSettings motoActionsSettings, Context context,
                             SensorHelper sensorHelper) {
        mMotoActionsSettings = motoActionsSettings;
        mSensorHelper = sensorHelper;
        mChopChopSensor = sensorHelper.getChopChopSensor();
        mProximitySensor = sensorHelper.getProximitySensor();
        mCameraManager = context.getSystemService(CameraManager.class);
        mCameraManager.registerTorchCallback(mTorchCallback, null);
        mVibrator = context.getSystemService(Vibrator.class);
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

            if (mRearCameraId != null) {
                return;
            }

            try {
                mCameraManager.setTorchMode(mRearCameraId, !mTorchEnabled);
                mTorchEnabled = !mTorchEnabled;
            } catch (CameraAccessException ignored) {
            }
            mVibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
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
