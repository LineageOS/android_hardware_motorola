/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.MainSwitchPreference;

public class DozePreferenceFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private SwitchPreferenceCompat mAlwaysOnDisplayPreference;

    private SwitchPreferenceCompat mHandwavePreference;
    private SwitchPreferenceCompat mPickUpPreference;
    private SwitchPreferenceCompat mPocketPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.doze_panel, rootKey);

        SharedPreferences prefs =
                getActivity().getSharedPreferences("doze_panel", Context.MODE_PRIVATE);
        if (savedInstanceState == null && !prefs.getBoolean("first_help_shown", false)) {
            showHelp();
        }

        boolean dozeEnabled = MotoActionsSettings.isDozeEnabled(getActivity());

        MainSwitchPreference switchBar = findPreference(MotoActionsSettings.DOZE_ENABLE);
        switchBar.setOnPreferenceChangeListener(this);
        switchBar.setChecked(dozeEnabled);

        mAlwaysOnDisplayPreference = findPreference(MotoActionsSettings.ALWAYS_ON_DISPLAY);
        mAlwaysOnDisplayPreference.setEnabled(dozeEnabled);
        mAlwaysOnDisplayPreference.setChecked(MotoActionsSettings.isAlwaysOnEnabled(getActivity()));
        mAlwaysOnDisplayPreference.setOnPreferenceChangeListener(this);

        mHandwavePreference = findPreference(MotoActionsSettings.GESTURE_IR_WAKEUP_KEY);
        mHandwavePreference.setEnabled(dozeEnabled);
        mHandwavePreference.setOnPreferenceChangeListener(this);

        mPickUpPreference = findPreference(MotoActionsSettings.GESTURE_PICK_UP_KEY);
        mPickUpPreference.setEnabled(dozeEnabled);
        mPickUpPreference.setOnPreferenceChangeListener(this);

        mPocketPreference = findPreference(MotoActionsSettings.GESTURE_POCKET_KEY);
        mPocketPreference.setEnabled(dozeEnabled);
        mPocketPreference.setOnPreferenceChangeListener(this);

        // Hide AOD if not supported and set all its dependents otherwise
        if (!MotoActionsSettings.alwaysOnDisplayAvailable(getActivity())) {
            getPreferenceScreen().removePreference(mAlwaysOnDisplayPreference);
        } else {
            PreferenceCategory ambientDisplayCategory = findPreference("ambient_display_key");
            ambientDisplayCategory.setDependency(MotoActionsSettings.ALWAYS_ON_DISPLAY);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean isChecked = (Boolean) newValue;
        if (MotoActionsSettings.ALWAYS_ON_DISPLAY.equals(preference.getKey())) {
            MotoActionsSettings.enableAlwaysOn(getActivity(), isChecked);
        } else if (MotoActionsSettings.DOZE_ENABLE.equals(preference.getKey())) {
            MotoActionsSettings.enableDoze(getActivity(), isChecked);

            if (!isChecked) {
                MotoActionsSettings.enableAlwaysOn(getActivity(), false);
                mAlwaysOnDisplayPreference.setChecked(false);
            }
            mAlwaysOnDisplayPreference.setEnabled(isChecked);

            mHandwavePreference.setEnabled(isChecked);
            mPickUpPreference.setEnabled(isChecked);
            mPocketPreference.setEnabled(isChecked);
        }

        return true;
    }

    private void showHelp() {
        AlertDialog helpDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.doze_settings_help_title)
                .setMessage(R.string.doze_settings_help_text)
                .setPositiveButton(R.string.dialog_ok,
                        (dialog, which) -> {
                            getActivity()
                                    .getSharedPreferences("doze_panel", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("first_help_shown", true)
                                    .commit();
                            dialog.cancel();
                        })
                .create();
        helpDialog.show();
    }
}
