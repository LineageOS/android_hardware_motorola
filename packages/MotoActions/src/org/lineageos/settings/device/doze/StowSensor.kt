/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_IR_WAKEUP_KEY
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_POCKET_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_STOW

class StowSensor(
    context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : ScreenStateNotifier {

    private val dozePulseAction: DozePulseAction = DozePulseAction(context)
    private val stowSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_STOW)!!

    private var lastStowed = false
    private var lastStowedTime = 0L

    override fun onScreenStateChanged(screenOn: Boolean) {
        if (screenOn) {
            if (enabled) {
                Log.d(TAG, "Disabling")
                sensorHelper.unregisterListener(stowListener)
                enabled = false
            }
        } else {
            if (
                (sharedPreferences.getBoolean(GESTURE_POCKET_KEY, true) ||
                    sharedPreferences.getBoolean(GESTURE_IR_WAKEUP_KEY, true)) && !enabled
            ) {
                Log.d(TAG, "Enabling")
                sensorHelper.registerListener(stowSensor, stowListener)
                enabled = true
            }
        }
        dozePulseAction.onStateChanged(false)
    }

    private val stowListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val thisStowed = event.values[0] != 0f
                if (thisStowed) {
                    lastStowedTime = event.timestamp
                } else if (lastStowed && shouldPulse(event.timestamp)) {
                    dozePulseAction.onStartPulse()
                }
                lastStowed = thisStowed
                Log.d(TAG, "event: $thisStowed")
            }

            private fun shouldPulse(timestamp: Long): Boolean {
                val delta = timestamp - lastStowedTime

                val irWakeupEnabled = sharedPreferences.getBoolean(GESTURE_IR_WAKEUP_KEY, true)
                val pocketGestureEnabled = sharedPreferences.getBoolean(GESTURE_POCKET_KEY, true)

                return when {
                    irWakeupEnabled && pocketGestureEnabled -> true
                    irWakeupEnabled -> delta < HANDWAVE_MAX_DELTA_NS
                    pocketGestureEnabled -> delta >= POCKET_MIN_DELTA_NS
                    else -> false
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    companion object {
        private const val TAG = "MotoActions-StowSensor"
        private const val HANDWAVE_MAX_DELTA_NS = 1000L * 1000 * 1000
        private const val POCKET_MIN_DELTA_NS = 5000L * 1000 * 1000
    }
}
