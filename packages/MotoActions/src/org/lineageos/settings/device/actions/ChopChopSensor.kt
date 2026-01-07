/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings
import org.lineageos.settings.device.SensorHelper

class ChopChopSensor(
    private val motoActionsSettings: MotoActionsSettings,
    private val sensorHelper: SensorHelper,
) : SensorEventListener, UpdatedStateNotifier {

    private val sensor: Sensor = sensorHelper.getChopChopSensor()
    private val prox: Sensor = sensorHelper.getProximitySensor()

    private var isEnabled = false
    private var proxIsCovered = false

    @Synchronized
    override fun updateState() {
        when {
            motoActionsSettings.isChopChopGestureEnabled() && !isEnabled -> {
                Log.d(TAG, "Enabling")
                sensorHelper.registerListener(sensor, this)
                sensorHelper.registerListener(prox, proxListener)
                isEnabled = true
            }
            !motoActionsSettings.isChopChopGestureEnabled() && isEnabled -> {
                Log.d(TAG, "Disabling")
                sensorHelper.unregisterListener(this)
                sensorHelper.unregisterListener(proxListener)
                isEnabled = false
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d(TAG, "chop chop triggered")
        if (proxIsCovered) {
            Log.d(TAG, "proximity sensor covered, ignoring chop-chop")
            return
        }
        motoActionsSettings.chopChopAction()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private val proxListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = kotlin.math.round(prox.maximumRange * 10f) / 10f
                proxIsCovered = event.values[0] < maxRange
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    companion object {
        private const val TAG = "MotoActions-ChopChopSensor"
    }
}
