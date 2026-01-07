/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import android.os.VibrationEffect
import android.os.Vibrator
import org.lineageos.settings.device.SensorAction

class TorchAction(context: Context) : SensorAction {

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

    private var rearCameraId: String? = null

    override fun action() {
        vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))

        rearCameraId?.let { id ->
            runCatching {
                cameraManager.setTorchMode(id, !torchEnabled)
                torchEnabled = !torchEnabled
            }
        }
    }

    companion object {
        private var torchEnabled = false
    }
}
