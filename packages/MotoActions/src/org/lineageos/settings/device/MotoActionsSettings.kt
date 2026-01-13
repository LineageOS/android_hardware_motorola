/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.hardware.display.AmbientDisplayConfiguration
import android.os.UserHandle
import android.provider.Settings

object MotoActionsSettings {

    const val GESTURE_CHOP_CHOP_KEY = "gesture_chop_chop"
    const val GESTURE_IR_SILENCER_KEY = "gesture_ir_silencer"
    const val GESTURE_FLIP_TO_MUTE_KEY = "gesture_flip_to_mute"
    const val GESTURE_LIFT_TO_SILENCE_KEY = "gesture_lift_to_silence"
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
