/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.stylus

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class Utils(private val context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)!!
    private val vibrator = context.getSystemService(Vibrator::class.java)!!
    private val packageManager = context.packageManager

    fun turnScreenOn() {
        powerManager.wakeUp(
            SystemClock.uptimeMillis(),
            PowerManager.WAKE_REASON_GESTURE,
            TAG
        )
    }

    fun turnScreenOff() {
        powerManager.goToSleep(SystemClock.uptimeMillis())
    }

    fun vibrateIfNeeded(effect: VibrationEffect) {
        vibrator.vibrate(
            effect,
            HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES
        )
    }

    fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let {
            try {
                context.startActivity(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $packageName", e)
            }
        } ?: Log.w(TAG, "No launch intent found for $packageName")
    }

    companion object {
        private const val TAG = "Utils"

        // Vibration attributes
        val HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

        // Vibration effects
        val VIBRATE_HEAVY_CLICK = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
    }
}
