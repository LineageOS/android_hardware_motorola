/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class DozePreferenceFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private lateinit var alwaysOnDisplayPreference: SwitchPreferenceCompat
    private lateinit var handwavePreference: SwitchPreferenceCompat
    private lateinit var pickUpPreference: SwitchPreferenceCompat
    private lateinit var pocketPreference: SwitchPreferenceCompat

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.doze_panel, rootKey)

        val prefs = requireActivity().getSharedPreferences("doze_panel", Context.MODE_PRIVATE)

        if (savedInstanceState == null && !prefs.getBoolean("first_help_shown", false)) {
            showHelp()
        }

        val dozeEnabled = MotoActionsSettings.isDozeEnabled(requireActivity())

        val switchBar: MainSwitchPreference = findPreference(MotoActionsSettings.DOZE_ENABLE)!!
        switchBar.onPreferenceChangeListener = this
        switchBar.isChecked = dozeEnabled

        alwaysOnDisplayPreference = findPreference(MotoActionsSettings.ALWAYS_ON_DISPLAY)!!
        alwaysOnDisplayPreference.isEnabled = dozeEnabled
        alwaysOnDisplayPreference.isChecked =
            MotoActionsSettings.isAlwaysOnEnabled(requireActivity())
        alwaysOnDisplayPreference.onPreferenceChangeListener = this

        handwavePreference = findPreference(MotoActionsSettings.GESTURE_IR_WAKEUP_KEY)!!
        handwavePreference.isEnabled = dozeEnabled
        handwavePreference.onPreferenceChangeListener = this

        pickUpPreference = findPreference(MotoActionsSettings.GESTURE_PICK_UP_KEY)!!
        pickUpPreference.isEnabled = dozeEnabled
        pickUpPreference.onPreferenceChangeListener = this

        pocketPreference = findPreference(MotoActionsSettings.GESTURE_POCKET_KEY)!!
        pocketPreference.isEnabled = dozeEnabled
        pocketPreference.onPreferenceChangeListener = this

        if (!MotoActionsSettings.alwaysOnDisplayAvailable(requireActivity())) {
            preferenceScreen.removePreference(alwaysOnDisplayPreference)
        } else {
            val ambientDisplayCategory: PreferenceCategory = findPreference("ambient_display_key")!!
            ambientDisplayCategory.dependency = MotoActionsSettings.ALWAYS_ON_DISPLAY
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val isChecked = newValue as Boolean

        when (preference.key) {
            MotoActionsSettings.ALWAYS_ON_DISPLAY -> {
                MotoActionsSettings.enableAlwaysOn(requireActivity(), isChecked)
            }

            MotoActionsSettings.DOZE_ENABLE -> {
                MotoActionsSettings.enableDoze(requireActivity(), isChecked)

                if (!isChecked) {
                    MotoActionsSettings.enableAlwaysOn(requireActivity(), false)
                    alwaysOnDisplayPreference.isChecked = false
                }

                alwaysOnDisplayPreference.isEnabled = isChecked
                handwavePreference.isEnabled = isChecked
                pickUpPreference.isEnabled = isChecked
                pocketPreference.isEnabled = isChecked
            }
        }

        return true
    }

    private fun showHelp() {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.doze_settings_help_title)
            .setMessage(R.string.doze_settings_help_text)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                requireActivity()
                    .getSharedPreferences("doze_panel", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("first_help_shown", true)
                    .commit()
                dialog.cancel()
            }
            .create()
            .show()
    }
}
