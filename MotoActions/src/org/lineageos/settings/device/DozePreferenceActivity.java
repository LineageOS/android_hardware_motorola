/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class DozePreferenceActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG_DOZE = "doze";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                        new DozePreferenceFragment(), TAG_DOZE)
                .commit();
    }
}
