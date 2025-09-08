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

    private val packageContext = context.createPackageContext(
        KeyHandler::class.java.getPackage()!!.name, 0
    )

    private val sharedPreferences
        get() = packageContext.getSharedPreferences(
            packageContext.packageName + "_preferences",
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
        )

    fun turnScreenOn() {
        powerManager.wakeUp(
            SystemClock.uptimeMillis(),
            PowerManager.WAKE_REASON_GESTURE,
            TAG
        )
    }

    fun vibrateIfNeeded(effect: VibrationEffect, isInsert: Boolean) {
        val vibrateKey = if (isInsert) KEY_VIBRATE_WHEN_INSERTED else KEY_VIBRATE_WHEN_REMOVED
        val vibrateEnabled = sharedPreferences.getBoolean(vibrateKey, true)
        if (vibrateEnabled) {
            vibrator.vibrate(
                effect,
                HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES
            )
        }
    }

    fun launchApp() {
        val packageName = sharedPreferences.getString(KEY_LAUNCH_APP, null)
        if (!packageName.isNullOrEmpty()) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.let {
                try {
                    packageContext.startActivity(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch $packageName", e)
                }
            } ?: Log.w(TAG, "No launch intent found for $packageName")
        }
    }

    companion object {
        private const val TAG = "Utils"

        // Preference keys
        const val KEY_VIBRATE_WHEN_REMOVED = "vibrate_when_removed"
        const val KEY_VIBRATE_WHEN_INSERTED = "vibrate_when_inserted"
        const val KEY_LAUNCH_APP = "launch_app"

        // Vibration attributes
        val HARDWARE_FEEDBACK_VIBRATION_ATTRIBUTES =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

        // Vibration effects
        val VIBRATE_HEAVY_CLICK = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
    }
}
