/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_FLIP_TO_MUTE_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_FLAT_DOWN
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_STOW

class FlipToMute(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : UpdatedStateNotifier {

    private val flatDownSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_FLAT_DOWN)!!
    private val stowSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_STOW)!!
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private var isEnabled = false
    private var isFlatDown = false
    private var isStowed = false
    private var filter = notificationManager.currentInterruptionFilter

    override fun updateState() {
        val enabled = sharedPreferences.getBoolean(GESTURE_FLIP_TO_MUTE_KEY, false)
        if (enabled == isEnabled) {
            return
        }
        if (enabled) {
            Log.d(TAG, "Enabling")
            sensorHelper.registerListener(flatDownSensor, flatDownListener)
            sensorHelper.registerListener(stowSensor, stowListener)
            context.registerReceiver(
                receiver,
                IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            )
        } else {
            Log.d(TAG, "Disabling")
            sensorHelper.unregisterListener(flatDownListener)
            sensorHelper.unregisterListener(stowListener)
            context.unregisterReceiver(receiver)
        }
        isEnabled = enabled
    }

    private val flatDownListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                isFlatDown = event.values[0] != 0f
                sensorChange()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val stowListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                isStowed = event.values[0] != 0f
                sensorChange()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private fun sensorChange() {
        Log.d(TAG, "event: $isFlatDown mIsStowed=$isStowed")

        when {
            isFlatDown && isStowed -> {
                notificationManager.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                )
                Log.d(TAG, "Interrupt filter: Allow priority")
            }
            !isFlatDown -> {
                notificationManager.setInterruptionFilter(filter)
                Log.d(TAG, "Interrupt filter: Restore")
            }
        }
    }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!isFlatDown && !isStowed) {
                    filter = notificationManager.currentInterruptionFilter
                    Log.d(TAG, "Interrupt filter: Backup")
                }
            }
        }

    companion object {
        private const val TAG = "FlipToMute"
    }
}
