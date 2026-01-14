/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
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
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_IR_SILENCER_KEY
import org.lineageos.settings.device.SensorHelper

class ProximitySilencer(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : UpdatedStateNotifier {

    private val telecomManager = context.getSystemService(TelecomManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val proximitySensor: Sensor = sensorHelper.getSensor(Sensor.TYPE_PROXIMITY)!!

    private var isEnabled = false
    private var isRinging = false
    private var ringStartedMs = 0L
    private var coveredRinging = false

    override fun updateState() {
        val enabled = sharedPreferences.getBoolean(GESTURE_IR_SILENCER_KEY, false)
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
                        sensorHelper.registerListener(proximitySensor, proximityListener)
                        isRinging = true
                        ringStartedMs = System.currentTimeMillis()
                    }
                    state != TelephonyManager.CALL_STATE_RINGING && isRinging -> {
                        Log.d(TAG, "Ringing stopped")
                        sensorHelper.unregisterListener(proximityListener)
                        isRinging = false
                    }
                }
            }
        }

    private val proximityListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = kotlin.math.round(proximitySensor.maximumRange * 10f) / 10f
                val isNear = event.values[0] < maxRange
                val now = System.currentTimeMillis()

                if (isNear) {
                    coveredRinging = isRinging && (now - ringStartedMs >= SILENCE_DELAY_MS)
                    return
                }

                if (isRinging) {
                    Log.d(TAG, "event: ${event.values[0]}, covered $coveredRinging")
                    if (coveredRinging) {
                        Log.d(TAG, "Silencing ringer")
                        telecomManager.silenceRinger()
                    } else {
                        Log.d(
                            TAG,
                            "Ignoring silence gesture: $now is too close to " +
                                "$ringStartedMs, delay=$SILENCE_DELAY_MS",
                        )
                    }
                    coveredRinging = false
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    companion object {
        private const val TAG = "ProximitySilencer"
        private const val SILENCE_DELAY_MS = 500
    }
}
