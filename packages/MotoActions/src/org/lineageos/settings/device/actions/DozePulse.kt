/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.UserHandle
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_IR_WAKEUP_KEY
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_PICK_UP_KEY
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_POCKET_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_FLAT_UP
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_STOW

class DozePulse(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : UpdatedStateNotifier {

    private val powerManager: PowerManager = context.getSystemService(PowerManager::class.java)
    private val wakeLock: WakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:WakeLock")
    private val flatUpSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_FLAT_UP)!!
    private val stowSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_STOW)!!

    private var isStowed = false
    private var lastStowed = false
    private var lastStowedTime = 0L

    private var lastFlatUp = false

    private var lastDozeTime: Long = 0L

    fun onScreenStateChanged(screenOn: Boolean) {
        if (!wakeLock.isHeld) {
            if (screenOn) {
                wakeLock.acquire()
            } else {
                wakeLock.release()
            }
        }

        if (screenOn) {
            Log.d(TAG, "Disabling")
            sensorHelper.unregisterListener(flatUpListener)
            sensorHelper.unregisterListener(stowListener)
        } else {
            Log.d(TAG, "Enabling")
            sensorHelper.registerListener(flatUpSensor, flatUpListener)
            sensorHelper.registerListener(stowSensor, stowListener)
            lastDozeTime = System.currentTimeMillis()
        }
    }

    @Synchronized
    override fun updateState() {
        onScreenStateChanged(powerManager.isInteractive)
    }

    private val flatUpListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val thisFlatUp = event.values[0] != 0f
                val pickUpGestureEnabled = sharedPreferences.getBoolean(GESTURE_PICK_UP_KEY, true)
                if (pickUpGestureEnabled && lastFlatUp && !thisFlatUp && !isStowed) {
                    onStartPulse()
                }
                lastFlatUp = thisFlatUp
                Log.d(TAG, "event: $thisFlatUp")
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val stowListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                isStowed = event.values[0] != 0f
                if (isStowed) {
                    lastStowedTime = event.timestamp
                } else if (lastStowed && shouldPulse(event.timestamp)) {
                    onStartPulse()
                }
                lastStowed = isStowed
                Log.d(TAG, "event: $isStowed")
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

    private fun onStartPulse() {
        if (
            !MotoActionsSettings.isDozeEnabled(context) ||
                MotoActionsSettings.isAlwaysOnEnabled(context)
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastDozeTime > DELAY_BETWEEN_DOZES_IN_MS) {
            lastDozeTime = now
            context.sendBroadcastAsUser(
                Intent("com.android.systemui.doze.pulse"),
                UserHandle.CURRENT,
            )
        }
    }

    companion object {
        private const val TAG = "DozePulse"
        private const val DELAY_BETWEEN_DOZES_IN_MS = 1500
        private const val HANDWAVE_MAX_DELTA_NS = 1000L * 1000 * 1000
        private const val POCKET_MIN_DELTA_NS = 5000L * 1000 * 1000
    }
}
