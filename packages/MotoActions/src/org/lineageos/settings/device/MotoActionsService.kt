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
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import org.lineageos.settings.device.actions.ChopChopSensor
import org.lineageos.settings.device.actions.FlipToMute
import org.lineageos.settings.device.actions.LiftToSilence
import org.lineageos.settings.device.actions.ProximitySilencer
import org.lineageos.settings.device.actions.UpdatedStateNotifier
import org.lineageos.settings.device.doze.DozePulseAction
import org.lineageos.settings.device.doze.FlatUpSensor
import org.lineageos.settings.device.doze.ScreenStateNotifier
import org.lineageos.settings.device.doze.StowSensor

class MotoActionsService : Service(), ScreenStateNotifier, UpdatedStateNotifier {

    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: WakeLock

    private val screenStateNotifiers = mutableListOf<ScreenStateNotifier>()
    private val updatedStateNotifiers = mutableListOf<UpdatedStateNotifier>()

    override fun onCreate() {
        Log.d(TAG, "Starting")

        val actionsSettings = MotoActionsSettings(this, this)
        val sensorHelper = SensorHelper(this)
        val dozePulseAction = DozePulseAction(this)

        screenStateNotifiers.add(dozePulseAction)

        screenStateNotifiers.add(StowSensor(actionsSettings, sensorHelper, dozePulseAction))
        screenStateNotifiers.add(FlatUpSensor(actionsSettings, sensorHelper, dozePulseAction))

        updatedStateNotifiers.add(ChopChopSensor(actionsSettings, sensorHelper))
        updatedStateNotifiers.add(ProximitySilencer(actionsSettings, this, sensorHelper))
        updatedStateNotifiers.add(FlipToMute(actionsSettings, this, sensorHelper))
        updatedStateNotifiers.add(LiftToSilence(actionsSettings, this, sensorHelper))

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

    override fun screenTurnedOn() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        screenStateNotifiers.forEach { it.screenTurnedOn() }
    }

    override fun screenTurnedOff() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        screenStateNotifiers.forEach { it.screenTurnedOff() }
    }

    override fun updateState() {
        if (powerManager.isInteractive) {
            screenTurnedOn()
        } else {
            screenTurnedOff()
        }
        updatedStateNotifiers.forEach { it.updateState() }
    }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> screenTurnedOff()
                    Intent.ACTION_SCREEN_ON -> screenTurnedOn()
                }
            }
        }

    companion object {
        private const val TAG = "MotoActions"
    }
}
