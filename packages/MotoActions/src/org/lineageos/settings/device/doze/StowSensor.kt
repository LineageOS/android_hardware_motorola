/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings
import org.lineageos.settings.device.SensorAction
import org.lineageos.settings.device.SensorHelper

class StowSensor(
    private val motoActionsSettings: MotoActionsSettings,
    private val sensorHelper: SensorHelper,
    private val sensorAction: SensorAction,
) : ScreenStateNotifier {

    private val stowSensor: Sensor = sensorHelper.getStowSensor()

    private var enabled = false
    private var lastStowed = false
    private var lastStowedTime = 0L

    override fun screenTurnedOn() {
        if (enabled) {
            Log.d(TAG, "Disabling")
            sensorHelper.unregisterListener(stowListener)
            enabled = false
        }
    }

    override fun screenTurnedOff() {
        if (
            (motoActionsSettings.isPocketGestureEnabled() ||
                motoActionsSettings.isIrWakeupEnabled()) && !enabled
        ) {
            Log.d(TAG, "Enabling")
            sensorHelper.registerListener(stowSensor, stowListener)
            enabled = true
        }
    }

    private val stowListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val thisStowed = event.values[0] != 0f
                if (thisStowed) {
                    lastStowedTime = event.timestamp
                } else if (lastStowed && shouldPulse(event.timestamp)) {
                    sensorAction.action()
                }
                lastStowed = thisStowed
                Log.d(TAG, "event: $thisStowed")
            }

            private fun shouldPulse(timestamp: Long): Boolean {
                val delta = timestamp - lastStowedTime

                val irWakeupEnabled = motoActionsSettings.isIrWakeupEnabled()
                val pocketGestureEnabled = motoActionsSettings.isPocketGestureEnabled()

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
