/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import org.lineageos.settings.device.MotoActionsSettings;
import org.lineageos.settings.device.SensorHelper;

public class FlipToMute implements UpdatedStateNotifier {
    private static final String TAG = "MotoActions-FlipToMute";

    private final NotificationManager mNotificationManager;
    private final MotoActionsSettings mMotoActionsSettings;
    private final SensorHelper mSensorHelper;
    private final Sensor mFlatDown;
    private final Sensor mStow;

    private boolean mIsEnabled;
    private boolean mIsFlatDown;
    private boolean mIsStowed;
    private int mFilter;
    private final Context mContext;

    public FlipToMute(MotoActionsSettings motoActionsSettings, Context context,
                      SensorHelper sensorHelper) {
        mMotoActionsSettings = motoActionsSettings;
        mContext = context;
        mSensorHelper = sensorHelper;
        mFlatDown = sensorHelper.getFlatDownSensor();
        mStow = sensorHelper.getStowSensor();
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mFilter = mNotificationManager.getCurrentInterruptionFilter();
    }

    @Override
    public void updateState() {
        if (mMotoActionsSettings.isFlipToMuteEnabled() && !mIsEnabled) {
            Log.d(TAG, "Enabling");
            mSensorHelper.registerListener(mFlatDown, mFlatDownListener);
            mSensorHelper.registerListener(mStow, mStowListener);
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED));
            mIsEnabled = true;
        } else if (!mMotoActionsSettings.isFlipToMuteEnabled() && mIsEnabled) {
            Log.d(TAG, "Disabling");
            mSensorHelper.unregisterListener(mFlatDownListener);
            mSensorHelper.unregisterListener(mStowListener);
            mContext.unregisterReceiver(mReceiver);
            mIsEnabled = false;
        }
    }

    private final SensorEventListener mFlatDownListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            mIsFlatDown = (event.values[0] != 0);
            sensorChange();
        }

        @Override
        public void onAccuracyChanged(Sensor mSensor, int accuracy) {
        }
    };

    private final SensorEventListener mStowListener = new SensorEventListener() {
        @Override
        public synchronized void onSensorChanged(SensorEvent event) {
            mIsStowed = (event.values[0] != 0);
            sensorChange();
        }

        @Override
        public void onAccuracyChanged(Sensor mSensor, int accuracy) {
        }
    };

    private void sensorChange() {
        Log.d(TAG, "event: " + mIsFlatDown + " mIsStowed=" + mIsStowed);

        if (mIsFlatDown && mIsStowed) {
            mNotificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            Log.d(TAG, "Interrupt filter: Allow priority");
        } else if (!mIsFlatDown) {
            mNotificationManager.setInterruptionFilter(mFilter);
            Log.d(TAG, "Interrupt filter: Restore");
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mIsFlatDown && !mIsStowed) {
                mFilter = mNotificationManager.getCurrentInterruptionFilter();
                Log.d(TAG, "Interrupt filter: Backup");
            }
        }
    };
}
