/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.stylus

import android.os.Bundle
import androidx.preference.*

class StylusSettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.stylus_panel, rootKey)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        return true
    }
}
