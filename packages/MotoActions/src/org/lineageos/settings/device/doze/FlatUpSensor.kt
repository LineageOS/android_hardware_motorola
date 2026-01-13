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
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_PICK_UP_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_FLAT_UP
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_STOW

class FlatUpSensor(
    context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : ScreenStateNotifier {

    private val dozePulseAction: DozePulseAction = DozePulseAction(context)
    private val flatUpSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_FLAT_UP)!!
    private val stowSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_STOW)!!

    private var isStowed = false
    private var lastFlatUp = false

    override fun screenTurnedOn() {
        if (enabled) {
            Log.d(TAG, "Disabling")
            sensorHelper.unregisterListener(flatUpListener)
            sensorHelper.unregisterListener(stowListener)
            enabled = false
        }
        dozePulseAction.onStateChanged(true)
    }

    override fun screenTurnedOff() {
        if (sharedPreferences.getBoolean(GESTURE_PICK_UP_KEY, true) && !enabled) {
            Log.d(TAG, "Enabling")
            sensorHelper.registerListener(flatUpSensor, flatUpListener)
            sensorHelper.registerListener(stowSensor, stowListener)
            enabled = true
        }
        dozePulseAction.onStateChanged(false)
    }

    private val flatUpListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val thisFlatUp = event.values[0] != 0f

                Log.d(TAG, "event: $thisFlatUp mLastFlatUp=$lastFlatUp mIsStowed=$isStowed")

                if (lastFlatUp && !thisFlatUp && !isStowed) {
                    dozePulseAction.onStartPulse()
                }
                lastFlatUp = thisFlatUp
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val stowListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                isStowed = event.values[0] != 0f
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    companion object {
        private const val TAG = "MotoActions-FlatUpSensor"
    }
}
