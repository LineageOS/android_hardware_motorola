/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017-2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;

import com.android.settingslib.widget.MainSwitchPreference;

public class DozePreferenceFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener, OnCheckedChangeListener {

    private MainSwitchPreference mSwitchBar;

    private SwitchPreference mAlwaysOnDisplayPreference;

    private SwitchPreference mHandwavePreference;
    private SwitchPreference mPickUpPreference;
    private SwitchPreference mPocketPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.doze_panel);

        SharedPreferences prefs =
                getActivity().getSharedPreferences("doze_panel", Activity.MODE_PRIVATE);
        if (savedInstanceState == null && !prefs.getBoolean("first_help_shown", false)) {
            showHelp();
        }

        boolean dozeEnabled = MotoActionsSettings.isDozeEnabled(getActivity());

        mSwitchBar = (MainSwitchPreference) findPreference(MotoActionsSettings.DOZE_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);
        mSwitchBar.setChecked(dozeEnabled);

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
        if (MotoActionsSettings.ALWAYS_ON_DISPLAY.equals(preference.getKey())) {
            MotoActionsSettings.enableAlwaysOn(getActivity(), (Boolean) newValue);
        }

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        MotoActionsSettings.enableDoze(getActivity(), isChecked);

        mSwitchBar.setChecked(isChecked);

        if (!isChecked) {
            MotoActionsSettings.enableAlwaysOn(getActivity(), false);
            mAlwaysOnDisplayPreference.setChecked(false);
        }
        mAlwaysOnDisplayPreference.setEnabled(isChecked);

        mHandwavePreference.setEnabled(isChecked);
        mPickUpPreference.setEnabled(isChecked);
        mPocketPreference.setEnabled(isChecked);
    }

    public static class HelpDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.doze_settings_help_title)
                    .setMessage(R.string.doze_settings_help_text)
                    .setNegativeButton(R.string.dialog_ok, (dialog, which) -> dialog.cancel())
                    .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            getActivity()
                    .getSharedPreferences("doze_panel", Activity.MODE_PRIVATE)
                    .edit()
                    .putBoolean("first_help_shown", true)
                    .apply();
        }
    }

    private void showHelp() {
        HelpDialogFragment fragment = new HelpDialogFragment();
        fragment.show(getFragmentManager(), "help_dialog");
    }
}
