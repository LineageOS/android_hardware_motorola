/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class SensorHelper(private val context: Context) {

    private val sensorManager =
        context.getSystemService(SensorManager::class.java).also { dumpSensorsList() }

    private fun dumpSensorsList() {
        runCatching {
                context.openFileOutput("sensors.txt", Context.MODE_PRIVATE).use { out ->
                    out.writer().use { writer ->
                        val sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL)
                        sensorList.forEach { sensor ->
                            writer.write(
                                "sensor ${sensor.type} = ${sensor.name} " +
                                    "max batch: ${sensor.fifoMaxEventCount} " +
                                    "isWakeUp: ${sensor.isWakeUpSensor}"
                            )
                        }
                    }
                }
            }
            .getOrElse { throw RuntimeException(it) }
    }

    fun getChopChopSensor(): Sensor =
        sensorManager.getDefaultSensor(SENSOR_TYPE_MMI_CHOP_CHOP, true)!!

    fun getFlatUpSensor(): Sensor = sensorManager.getDefaultSensor(SENSOR_TYPE_MMI_FLAT_UP, true)!!

    fun getFlatDownSensor(): Sensor =
        sensorManager.getDefaultSensor(SENSOR_TYPE_MMI_FLAT_DOWN, true)!!

    fun getProximitySensor(): Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)!!

    fun getStowSensor(): Sensor = sensorManager.getDefaultSensor(SENSOR_TYPE_MMI_STOW, true)!!

    fun registerListener(sensor: Sensor, listener: SensorEventListener) {
        if (
            !sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                BATCH_LATENCY_IN_MS * 1000,
            )
        ) {
            Log.e(TAG, "Failed to registerListener for sensor $sensor")
        }
    }

    fun unregisterListener(listener: SensorEventListener) {
        sensorManager.unregisterListener(listener)
    }

    companion object {
        private const val TAG = "MotoActions"

        private const val SENSOR_TYPE_MMI_CHOP_CHOP = 65546
        private const val SENSOR_TYPE_MMI_FLAT_UP = 65537
        private const val SENSOR_TYPE_MMI_FLAT_DOWN = 65538
        private const val SENSOR_TYPE_MMI_STOW = 65539

        private const val BATCH_LATENCY_IN_MS = 100
    }
}
