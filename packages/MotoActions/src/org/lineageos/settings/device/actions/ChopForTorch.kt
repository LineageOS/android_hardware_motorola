/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_CHOP_CHOP_KEY
import org.lineageos.settings.device.SensorHelper
import org.lineageos.settings.device.SensorHelper.Companion.SENSOR_TYPE_MMI_CHOP_CHOP

class ChopForTorch(
    context: Context,
    private val sharedPreferences: SharedPreferences,
    private val sensorHelper: SensorHelper,
) : UpdatedStateNotifier {

    private val cameraManager =
        context.getSystemService(CameraManager::class.java).apply {
            val callback =
                object : TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId != rearCameraId) return
                        torchEnabled = enabled
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId != rearCameraId) return
                        torchEnabled = false
                    }
                }
            registerTorchCallback(callback, null)
            runCatching {
                cameraIdList.forEach { cameraId ->
                    val characteristics = getCameraCharacteristics(cameraId)
                    val orientation = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (orientation == CameraCharacteristics.LENS_FACING_BACK) {
                        rearCameraId = cameraId
                        return@forEach
                    }
                }
            }
        }
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val chopChopSensor: Sensor = sensorHelper.getSensor(SENSOR_TYPE_MMI_CHOP_CHOP)!!
    private val proximitySensor: Sensor = sensorHelper.getSensor(Sensor.TYPE_PROXIMITY)!!

    private var isEnabled = false
    private var proxIsCovered = false
    private var torchEnabled = false
    private var rearCameraId: String? = null

    @Synchronized
    override fun updateState() {
        val enabled = sharedPreferences.getBoolean(GESTURE_CHOP_CHOP_KEY, true)
        if (enabled == isEnabled) {
            return
        }
        if (enabled) {
            Log.d(TAG, "Enabling")
            sensorHelper.registerListener(chopChopSensor, chopChopListener)
            sensorHelper.registerListener(proximitySensor, proximityListener)
        } else {
            Log.d(TAG, "Disabling")
            sensorHelper.unregisterListener(chopChopListener)
            sensorHelper.unregisterListener(proximityListener)
        }
        isEnabled = enabled
    }

    private val chopChopListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                Log.d(TAG, "chop chop triggered")
                if (proxIsCovered) {
                    Log.d(TAG, "proximity sensor covered, ignoring chop-chop")
                    return
                }
                vibrator.vibrate(
                    VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE)
                )

                rearCameraId?.let { id ->
                    runCatching {
                        cameraManager.setTorchMode(id, !torchEnabled)
                        torchEnabled = !torchEnabled
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    private val proximityListener =
        object : SensorEventListener {
            @Synchronized
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = kotlin.math.round(proximitySensor.maximumRange * 10f) / 10f
                proxIsCovered = event.values[0] < maxRange
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

    companion object {
        private const val TAG = "ChopForTorch"
    }
}
