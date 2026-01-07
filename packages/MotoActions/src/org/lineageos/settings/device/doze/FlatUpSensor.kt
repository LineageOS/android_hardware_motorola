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

class FlatUpSensor(
    private val motoActionsSettings: MotoActionsSettings,
    private val sensorHelper: SensorHelper,
    private val sensorAction: SensorAction,
) : ScreenStateNotifier {

    private val flatUpSensor: Sensor = sensorHelper.getFlatUpSensor()
    private val stowSensor: Sensor = sensorHelper.getStowSensor()

    private var enabled = false
    private var isStowed = false
    private var lastFlatUp = false

    override fun screenTurnedOn() {
        if (enabled) {
            Log.d(TAG, "Disabling")
            sensorHelper.unregisterListener(flatUpListener)
            sensorHelper.unregisterListener(stowListener)
            enabled = false
        }
    }

    override fun screenTurnedOff() {
        if (motoActionsSettings.isPickUpEnabled() && !enabled) {
            Log.d(TAG, "Enabling")
            sensorHelper.registerListener(flatUpSensor, flatUpListener)
            sensorHelper.registerListener(stowSensor, stowListener)
            enabled = true
        }
    }

    private val flatUpListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val thisFlatUp = event.values[0] != 0f

                Log.d(TAG, "event: $thisFlatUp mLastFlatUp=$lastFlatUp mIsStowed=$isStowed")

                if (lastFlatUp && !thisFlatUp && !isStowed) {
                    sensorAction.action()
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
