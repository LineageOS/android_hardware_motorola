/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

public class LiftToSilence extends PhoneStateListener implements UpdatedStateNotifier {
    private static final String TAG = "MotoActions-LiftToSilence";

    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final Sensor mFlatUpSensor;
    private final Sensor mStowSensor;

    private final TelecomManager mTelecomManager;
    private final TelephonyManager mTelephonyManager;

    private boolean mIsRinging;
    private boolean mIsStowed;
    private boolean mLastFlatUp;

    public LiftToSilence(MotoActionsSettings motoActionsSettings, Context context,
                         SensorHelper sensorHelper) {
        mMotoActionsSettings = motoActionsSettings;
        mSensorHelper = sensorHelper;
        mFlatUpSensor = sensorHelper.getFlatUpSensor();
        mStowSensor = sensorHelper.getStowSensor();
        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
    }

    @Override
    public void updateState() {
        if (mMotoActionsSettings.isLiftToSilenceEnabled()) {
            mTelephonyManager.listen(this, LISTEN_CALL_STATE);
        } else {
            mTelephonyManager.listen(this, 0);
        }
    }

    @Override
    public synchronized void onCallStateChanged(int state, String incomingNumber) {
        if (state == TelephonyManager.CALL_STATE_RINGING && !mIsRinging) {
            Log.d(TAG, "Ringing started");
            mSensorHelper.registerListener(mFlatUpSensor, mFlatUpListener);
            mSensorHelper.registerListener(mStowSensor, mStowListener);
            mIsRinging = true;
        } else if (state != TelephonyManager.CALL_STATE_RINGING && mIsRinging) {
            Log.d(TAG, "Ringing stopped");
            mSensorHelper.unregisterListener(mFlatUpListener);
            mSensorHelper.unregisterListener(mStowListener);
            mIsRinging = false;
        }
    }

    private final SensorEventListener mFlatUpListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            boolean thisFlatUp = (event.values[0] != 0);

            Log.d(TAG, "event: " + thisFlatUp + " mLastFlatUp=" + mLastFlatUp + " mIsStowed=" +
                    mIsStowed);

            if (mLastFlatUp && !thisFlatUp && !mIsStowed) {
                mTelecomManager.silenceRinger();
            }
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
