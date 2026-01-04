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
import android.util.Log
import androidx.preference.PreferenceManager
import org.lineageos.settings.device.actions.ChopForTorch
import org.lineageos.settings.device.actions.DozePulse
import org.lineageos.settings.device.actions.FlipToMute
import org.lineageos.settings.device.actions.LiftToSilence
import org.lineageos.settings.device.actions.ProximitySilencer
import org.lineageos.settings.device.actions.UpdatedStateNotifier

class MotoActionsService : Service() {

    private lateinit var dozePulse: DozePulse

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

        dozePulse = DozePulse(this, sharedPrefs, sensorHelper)

        updatedStateNotifiers.add(dozePulse)
        updatedStateNotifiers.add(ChopForTorch(this, sharedPrefs, sensorHelper))
        updatedStateNotifiers.add(ProximitySilencer(this, sharedPrefs, sensorHelper))
        updatedStateNotifiers.add(FlipToMute(this, sharedPrefs, sensorHelper))
        updatedStateNotifiers.add(LiftToSilence(this, sharedPrefs, sensorHelper))

        val filter =
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        registerReceiver(screenStateReceiver, filter)

        updateState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateState() {
        updatedStateNotifiers.forEach { it.updateState() }
    }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> dozePulse.onScreenStateChanged(false)
                    Intent.ACTION_SCREEN_ON -> dozePulse.onScreenStateChanged(true)
                }
            }
        }

    companion object {
        private const val TAG = "MotoActions"
    }
}
