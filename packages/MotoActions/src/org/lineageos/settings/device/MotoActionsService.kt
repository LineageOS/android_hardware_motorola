/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.preference.PreferenceManager
import org.lineageos.settings.device.actions.ChopChopSensor
import org.lineageos.settings.device.actions.FlipToMute
import org.lineageos.settings.device.actions.LiftToSilence
import org.lineageos.settings.device.actions.ProximitySilencer
import org.lineageos.settings.device.actions.UpdatedStateNotifier
import org.lineageos.settings.device.doze.FlatUpSensor
import org.lineageos.settings.device.doze.ScreenStateNotifier
import org.lineageos.settings.device.doze.StowSensor

class MotoActionsService : Service() {

    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: WakeLock

    private val screenStateNotifiers = mutableListOf<ScreenStateNotifier>()
    private val updatedStateNotifiers = mutableListOf<UpdatedStateNotifier>()

    override fun onCreate() {
        Log.d(TAG, "Starting")

        val sharedPrefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this).apply {
                val prefListener = OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        MotoActionsSettings.GESTURE_CHOP_CHOP_KEY,
                        MotoActionsSettings.GESTURE_IR_WAKEUP_KEY,
                        MotoActionsSettings.GESTURE_PICK_UP_KEY,
                        MotoActionsSettings.GESTURE_POCKET_KEY,
                        MotoActionsSettings.GESTURE_IR_SILENCER_KEY,
                        MotoActionsSettings.GESTURE_FLIP_TO_MUTE_KEY,
                        MotoActionsSettings.GESTURE_LIFT_TO_SILENCE_KEY -> updateState()
                    }
                }
                registerOnSharedPreferenceChangeListener(prefListener)
            }
        val sensorHelper = SensorHelper(this)

        screenStateNotifiers.add(StowSensor(this, sharedPrefs, sensorHelper))
        screenStateNotifiers.add(FlatUpSensor(this, sharedPrefs, sensorHelper))

        updatedStateNotifiers.add(ChopChopSensor(this, sharedPrefs, sensorHelper))
        updatedStateNotifiers.add(ProximitySilencer(this, sharedPrefs, sensorHelper))
        updatedStateNotifiers.add(FlipToMute(this, sharedPrefs, sensorHelper))
        updatedStateNotifiers.add(LiftToSilence(this, sharedPrefs, sensorHelper))

        powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:WakeLock")

        val filter =
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        registerReceiver(screenStateReceiver, filter)

        updateState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onScreenStateChanged(screenOn: Boolean) {
        if (!wakeLock.isHeld) {
            if (screenOn) {
                wakeLock.acquire()
            } else {
                wakeLock.release()
            }
        }
        screenStateNotifiers.forEach { it.onScreenStateChanged(screenOn) }
    }

    private fun updateState() {
        onScreenStateChanged(powerManager.isInteractive)
        updatedStateNotifiers.forEach { it.updateState() }
    }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenStateChanged(false)
                    Intent.ACTION_SCREEN_ON -> onScreenStateChanged(true)
                }
            }
        }

    companion object {
        private const val TAG = "MotoActions"
    }
}
