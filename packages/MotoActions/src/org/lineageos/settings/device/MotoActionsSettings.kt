/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.hardware.display.AmbientDisplayConfiguration
import android.os.UserHandle
import android.provider.Settings
import androidx.preference.PreferenceManager
import org.lineageos.settings.device.actions.TorchAction
import org.lineageos.settings.device.actions.UpdatedStateNotifier

class MotoActionsSettings(
    private val context: Context,
    private val updatedStateNotifier: UpdatedStateNotifier,
) {

    private var chopChopEnabled = false
    private var pickUpGestureEnabled = false
    private var pocketGestureEnabled = false
    private var irWakeUpEnabled = false
    private var irSilencerEnabled = false
    private var flipToMuteEnabled = false
    private var liftToSilenceEnabled = false

    private val prefListener = OnSharedPreferenceChangeListener { sharedPrefs, key ->
        var updated = true

        when (key) {
            GESTURE_CHOP_CHOP_KEY ->
                chopChopEnabled = sharedPrefs.getBoolean(GESTURE_CHOP_CHOP_KEY, true)
            GESTURE_IR_WAKEUP_KEY ->
                irWakeUpEnabled = sharedPrefs.getBoolean(GESTURE_IR_WAKEUP_KEY, true)
            GESTURE_PICK_UP_KEY ->
                pickUpGestureEnabled = sharedPrefs.getBoolean(GESTURE_PICK_UP_KEY, true)
            GESTURE_POCKET_KEY ->
                pocketGestureEnabled = sharedPrefs.getBoolean(GESTURE_POCKET_KEY, true)
            GESTURE_IR_SILENCER_KEY ->
                irSilencerEnabled = sharedPrefs.getBoolean(GESTURE_IR_SILENCER_KEY, false)
            GESTURE_FLIP_TO_MUTE_KEY ->
                flipToMuteEnabled = sharedPrefs.getBoolean(GESTURE_FLIP_TO_MUTE_KEY, false)
            GESTURE_LIFT_TO_SILENCE_KEY ->
                liftToSilenceEnabled = sharedPrefs.getBoolean(GESTURE_LIFT_TO_SILENCE_KEY, false)
            else -> updated = false
        }

        if (updated) {
            updatedStateNotifier.updateState()
        }
    }

    init {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        loadPreferences(sharedPrefs)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun isChopChopGestureEnabled() = chopChopEnabled

    fun isAlwaysOnEnabled(): Boolean = isAlwaysOnEnabled(context)

    fun isDozeEnabled(): Boolean = isDozeEnabled(context)

    fun isIrWakeupEnabled(): Boolean = isDozeEnabled() && !isAlwaysOnEnabled() && irWakeUpEnabled

    fun isPickUpEnabled(): Boolean = isDozeEnabled() && !isAlwaysOnEnabled() && pickUpGestureEnabled

    fun isPocketGestureEnabled(): Boolean =
        isDozeEnabled() && !isAlwaysOnEnabled() && pocketGestureEnabled

    fun isIrSilencerEnabled() = irSilencerEnabled

    fun isFlipToMuteEnabled() = flipToMuteEnabled

    fun isLiftToSilenceEnabled() = liftToSilenceEnabled

    fun chopChopAction() {
        TorchAction(context).action()
    }

    private fun loadPreferences(sharedPreferences: SharedPreferences) {
        chopChopEnabled = sharedPreferences.getBoolean(GESTURE_CHOP_CHOP_KEY, true)
        irWakeUpEnabled = sharedPreferences.getBoolean(GESTURE_IR_WAKEUP_KEY, true)
        pickUpGestureEnabled = sharedPreferences.getBoolean(GESTURE_PICK_UP_KEY, true)
        pocketGestureEnabled = sharedPreferences.getBoolean(GESTURE_POCKET_KEY, true)
        irSilencerEnabled = sharedPreferences.getBoolean(GESTURE_IR_SILENCER_KEY, false)
        flipToMuteEnabled = sharedPreferences.getBoolean(GESTURE_FLIP_TO_MUTE_KEY, false)
        liftToSilenceEnabled = sharedPreferences.getBoolean(GESTURE_LIFT_TO_SILENCE_KEY, false)
    }

    companion object {
        private const val GESTURE_CHOP_CHOP_KEY = "gesture_chop_chop"
        private const val GESTURE_IR_SILENCER_KEY = "gesture_ir_silencer"
        private const val GESTURE_FLIP_TO_MUTE_KEY = "gesture_flip_to_mute"
        private const val GESTURE_LIFT_TO_SILENCE_KEY = "gesture_lift_to_silence"

        const val GESTURE_IR_WAKEUP_KEY = "gesture_hand_wave"
        const val GESTURE_PICK_UP_KEY = "gesture_pick_up"
        const val GESTURE_POCKET_KEY = "gesture_pocket"

        const val DOZE_ENABLE = "doze_enable"
        const val ALWAYS_ON_DISPLAY = "always_on_display"

        fun isAlwaysOnEnabled(context: Context): Boolean =
            Settings.Secure.getIntForUser(
                context.contentResolver,
                Settings.Secure.DOZE_ALWAYS_ON,
                0,
                UserHandle.USER_CURRENT,
            ) != 0

        fun alwaysOnDisplayAvailable(context: Context): Boolean =
            AmbientDisplayConfiguration(context).alwaysOnAvailable()

        fun enableAlwaysOn(context: Context, enable: Boolean): Boolean =
            Settings.Secure.putIntForUser(
                context.contentResolver,
                Settings.Secure.DOZE_ALWAYS_ON,
                if (enable) 1 else 0,
                UserHandle.USER_CURRENT,
            )

        fun enableDoze(context: Context, enable: Boolean): Boolean =
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.DOZE_ENABLED,
                if (enable) 1 else 0,
            )

        fun isDozeEnabled(context: Context): Boolean =
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.DOZE_ENABLED, 1) != 0
    }
}
