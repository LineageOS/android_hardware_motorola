/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_LIFT_TO_SILENCE_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_FLAT_UP
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_STOW

class LiftToSilence(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : UpdatedStateNotifier {

    private val telecomManager = context.getSystemService(TelecomManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val flatUpSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_FLAT_UP)!!
    private val stowSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_STOW)!!

    private var isEnabled = false
    private var isRinging = false
    private var isStowed = false
    private var lastFlatUp = false

    override fun updateState() {
        val enabled = sharedPreferences.getBoolean(GESTURE_LIFT_TO_SILENCE_KEY, false)
        if (enabled == isEnabled) {
            return
        }
        if (enabled) {
            Log.d(TAG, "Enabling")
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callStateListener)
        } else {
            Log.d(TAG, "Disabling")
            telephonyManager.unregisterTelephonyCallback(callStateListener)
        }
        isEnabled = enabled
    }

    private val callStateListener =
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            @Synchronized
            override fun onCallStateChanged(state: Int) {
                when {
                    state == TelephonyManager.CALL_STATE_RINGING && !isRinging -> {
                        Log.d(TAG, "Ringing started")
                        sensorHelper.registerListener(flatUpSensor, flatUpListener)
                        sensorHelper.registerListener(stowSensor, stowListener)
                        isRinging = true
                    }
                    state != TelephonyManager.CALL_STATE_RINGING && isRinging -> {
                        Log.d(TAG, "Ringing stopped")
                        sensorHelper.unregisterListener(flatUpListener)
                        sensorHelper.unregisterListener(stowListener)
                        isRinging = false
                    }
                }
            }
        }

    private val flatUpListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val thisFlatUp = event.values[0] != 0f

                Log.d(TAG, "event: $thisFlatUp mLastFlatUp=$lastFlatUp mIsStowed=$isStowed")

                if (lastFlatUp && !thisFlatUp && !isStowed) {
                    telecomManager.silenceRinger()
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
        private const val TAG = "MotoActions-LiftToSilence"
    }
}
