/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings
import org.lineageos.settings.device.SensorHelper

class LiftToSilence(
    private val motoActionsSettings: MotoActionsSettings,
    context: Context,
    private val sensorHelper: SensorHelper,
) : PhoneStateListener(), UpdatedStateNotifier {

    private val telecomManager = context.getSystemService(TelecomManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val flatUpSensor: Sensor = sensorHelper.getFlatUpSensor()
    private val stowSensor: Sensor = sensorHelper.getStowSensor()

    private var isRinging = false
    private var isStowed = false
    private var lastFlatUp = false

    override fun updateState() {
        if (motoActionsSettings.isLiftToSilenceEnabled()) {
            telephonyManager.listen(this, LISTEN_CALL_STATE)
        } else {
            telephonyManager.listen(this, 0)
        }
    }

    @Synchronized
    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
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
