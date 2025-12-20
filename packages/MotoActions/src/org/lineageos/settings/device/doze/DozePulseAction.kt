/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze

import android.content.Context
import android.content.Intent
import android.os.UserHandle

class DozePulseAction(private val context: Context) {

    private var lastDoze: Long = 0

    fun onStateChanged(enabled: Boolean) {
        if (!enabled) {
            lastDoze = System.currentTimeMillis()
        }
    }

    fun onStartPulse() {
        val now = System.currentTimeMillis()
        if (now - lastDoze > DELAY_BETWEEN_DOZES_IN_MS) {
            lastDoze = now
            context.sendBroadcastAsUser(
                Intent("com.android.systemui.doze.pulse"),
                UserHandle.CURRENT,
            )
        }
    }

    companion object {
        private const val TAG = "MotoActions"
        private const val DELAY_BETWEEN_DOZES_IN_MS = 1500
    }
}
