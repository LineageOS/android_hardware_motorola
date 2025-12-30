/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions;

import static android.telephony.TelephonyManager.CALL_STATE_RINGING;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.telecom.TelecomManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

import java.util.concurrent.Executor;

public class ProximitySilencer implements UpdatedStateNotifier {
    private static final String TAG = "MotoActions-ProximitySilencer";

    private static final int SILENCE_DELAY_MS = 500;

    private final TelecomManager mTelecomManager;
    private final TelephonyManager mTelephonyManager;
    private final PhoneTelephonyCallback mTelephonyCallback;
    private final Executor mExecutor;
    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final Sensor mProximitySensor;
    private boolean mIsRinging;
    private long mRingStartedMs;
    private boolean mCoveredRinging;

    public ProximitySilencer(MotoActionsSettings motoActionsSettings, Context context,
                             SensorHelper sensorHelper) {
        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mTelephonyCallback = new PhoneTelephonyCallback();
        mExecutor = context.getMainExecutor();

        mMotoActionsSettings = motoActionsSettings;
        mSensorHelper = sensorHelper;
        mProximitySensor = sensorHelper.getProximitySensor();
        mCoveredRinging = false;
        mIsRinging = false;
    }

    @Override
    public void updateState() {
        if (mMotoActionsSettings.isIrSilencerEnabled()) {
            mTelephonyManager.registerTelephonyCallback(mExecutor, mTelephonyCallback);
        } else {
            mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        }
    }

    private class PhoneTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.CallStateListener {
        @Override
        public synchronized void onCallStateChanged(int state) {
            if (state == TelephonyManager.CALL_STATE_RINGING && !mIsRinging) {
                Log.d(TAG, "Ringing started");
                mSensorHelper.registerListener(mProximitySensor, mProximityListener);
                mIsRinging = true;
                mRingStartedMs = System.currentTimeMillis();
            } else if (state != TelephonyManager.CALL_STATE_RINGING && mIsRinging) {
                Log.d(TAG, "Ringing stopped");
                mSensorHelper.unregisterListener(mProximityListener);
                mIsRinging = false;
            }
        }
    };

    private final SensorEventListener mProximityListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            float maxRange = Math.round(mProximitySensor.getMaximumRange() * 10f) / 10f;
            boolean isNear = event.values[0] < maxRange;
            long now = System.currentTimeMillis();

            if (isNear) {
                mCoveredRinging = mIsRinging && (now - mRingStartedMs >= SILENCE_DELAY_MS);
                return;
            }

            if (mIsRinging) {
                Log.d(TAG, "event: " + event.values[0] + ", " + " covered " + mCoveredRinging);
                if (mCoveredRinging) {
                    Log.d(TAG, "Silencing ringer");
                    mTelecomManager.silenceRinger();
                } else {
                    Log.d(TAG, "Ignoring silence gesture: " + now + " is too close to " +
                            mRingStartedMs + ", delay=" + SILENCE_DELAY_MS);
                }
                mCoveredRinging = false;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
