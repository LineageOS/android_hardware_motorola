/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;

public class SensorHelper {
    private static final String TAG = "MotoActions";

    private static final int SENSOR_TYPE_MMI_CHOP_CHOP = 65546;
    private static final int SENSOR_TYPE_MMI_FLAT_UP = 65537;
    private static final int SENSOR_TYPE_MMI_FLAT_DOWN = 65538;
    private static final int SENSOR_TYPE_MMI_STOW = 65539;

    private static final int BATCH_LATENCY_IN_MS = 100;

    private final Context mContext;
    private final SensorManager mSensorManager;
    private HashMap<Integer, Sensor> mSensorMap = new HashMap<Integer, Sensor>();

    public SensorHelper(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        dumpSensorsList();
    }

    private void dumpSensorsList() {
        try {
            FileOutputStream out = mContext.openFileOutput("sensors.txt", Context.MODE_PRIVATE);
            OutputStreamWriter writer = new OutputStreamWriter(out);

            List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
            for (Sensor sensor : sensorList) {
                writer.write("sensor " + sensor.getType() + " = " + sensor.getName()
                        + " max batch: " + sensor.getFifoMaxEventCount() + " isWakeUp: "
                        + sensor.isWakeUpSensor() + "\n");
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Sensor getSensor(int type) {
        if (!mSensorMap.containsKey(type)) {
            mSensorMap.put(type, mSensorManager.getDefaultSensor(type, true));
        }
        return mSensorMap.get(type);
    }

    public Sensor getChopChopSensor() {
        return getSensor(SENSOR_TYPE_MMI_CHOP_CHOP);
    }

    public Sensor getFlatUpSensor() {
        return getSensor(SENSOR_TYPE_MMI_FLAT_UP);
    }

    public Sensor getFlatDownSensor() {
        return getSensor(SENSOR_TYPE_MMI_FLAT_DOWN);
    }

    public Sensor getProximitySensor() {
        return getSensor(Sensor.TYPE_PROXIMITY);
    }

    public Sensor getStowSensor() {
        return getSensor(SENSOR_TYPE_MMI_STOW);
    }

    public void registerListener(Sensor sensor, SensorEventListener listener) {
        if (!mSensorManager.registerListener(listener, sensor,
                SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_IN_MS * 1000)) {
            Log.e(TAG, "Failed to registerListener for sensor " + sensor);
        }
    }

    public void unregisterListener(SensorEventListener listener) {
        mSensorManager.unregisterListener(listener);
    }
}
