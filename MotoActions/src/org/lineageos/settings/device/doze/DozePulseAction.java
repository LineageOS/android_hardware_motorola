/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

public class DozePulseAction {
    private static final String TAG = "DozePulseAction";

    private static final int DELAY_BETWEEN_DOZES_IN_MS = 1500;

    private final Context mContext;

    private long mLastDoze;

    public DozePulseAction(Context context) {
        mContext = context;
    }

    public void onStateChanged(boolean enabled) {
        if (!enabled) {
            mLastDoze = System.currentTimeMillis();
        }
    }

    public void onStartPulse(boolean canPulse) {
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
