/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import org.lineageos.settings.device.SensorAction

class DozePulseAction(private val context: Context) : SensorAction, ScreenStateNotifier {

    override fun screenTurnedOn() {}

    override fun screenTurnedOff() {
        lastDoze = System.currentTimeMillis()
    }

    override fun action() {
        if (mayDoze()) {
            Log.d(TAG, "Sending doze.pulse intent")
            val pulseIntent = Intent("com.android.systemui.doze.pulse")
            context.sendBroadcastAsUser(pulseIntent, UserHandle.CURRENT)
        }
    }

    @Synchronized
    fun mayDoze(): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastDoze > DELAY_BETWEEN_DOZES_IN_MS) {
            Log.d(TAG, "Allowing doze")
            lastDoze = now
            true
        } else {
            Log.d(TAG, "Denying doze")
            false
        }
    }

    companion object {
        private const val TAG = "MotoActions"
        private const val DELAY_BETWEEN_DOZES_IN_MS = 1500
        private var lastDoze: Long = 0
    }
}
