/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.actions

import android.content.Context
import android.content.SharedPreferences
import android.hardware.devicestate.DeviceState
import android.hardware.devicestate.DeviceStateManager
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import org.lineageos.settings.device.MotoActionsSettings
import org.lineageos.settings.device.MotoActionsSettings.GESTURE_OPEN_TO_ANSWER_KEY

class OpenToAnswer(private val context: Context, private val sharedPreferences: SharedPreferences) :
    UpdatedStateNotifier {

    private val deviceStateManager = context.getSystemService(DeviceStateManager::class.java)
    private val telecomManager = context.getSystemService(TelecomManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val foldedStates =
        context.resources.getIntArray(com.android.internal.R.array.config_foldedDeviceStates)
    private val openStates =
        context.resources.getIntArray(com.android.internal.R.array.config_openDeviceStates)

    private var isEnabled = false
    private var callState = TelephonyManager.CALL_STATE_IDLE
    private var isFolded: Boolean? = null

    @Synchronized
    override fun updateState() {
        val enabled =
            MotoActionsSettings.isFoldable(context) &&
                sharedPreferences.getBoolean(GESTURE_OPEN_TO_ANSWER_KEY, true)
        if (enabled == isEnabled) {
            return
        }
        if (enabled) {
            Log.d(TAG, "Enabling")
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callStateListener)
            deviceStateManager.registerCallback(context.mainExecutor, deviceStateCallback)
        } else {
            Log.d(TAG, "Disabling")
            telephonyManager.unregisterTelephonyCallback(callStateListener)
            deviceStateManager.unregisterCallback(deviceStateCallback)
            isFolded = null
        }
        isEnabled = enabled
    }

    private val callStateListener =
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            @Synchronized
            override fun onCallStateChanged(state: Int) {
                callState = state
            }
        }

    private val deviceStateCallback =
        object : DeviceStateManager.DeviceStateCallback {
            @Synchronized
            override fun onDeviceStateChanged(state: DeviceState) {
                // Half folded states are ignored, only a full open or close counts
                val folded =
                    when (state.identifier) {
                        in foldedStates -> true
                        in openStates -> false
                        else -> return
                    }
                val wasFolded = isFolded
                isFolded = folded

                // The first callback reports the current state, not a change
                if (wasFolded == null || wasFolded == folded) {
                    return
                }

                Log.d(TAG, "event: folded=$folded callState=$callState")

                when {
                    !folded && callState == TelephonyManager.CALL_STATE_RINGING -> {
                        Log.d(TAG, "Answering call")
                        telecomManager.acceptRingingCall()
                    }
                    folded && callState != TelephonyManager.CALL_STATE_IDLE -> {
                        Log.d(TAG, "Ending call")
                        telecomManager.endCall()
                    }
                }
            }
        }

    companion object {
        private const val TAG = "OpenToAnswer"
    }
}
