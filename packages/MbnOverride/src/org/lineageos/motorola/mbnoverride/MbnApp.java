/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.mbnoverride;

import android.app.Application;
import android.content.Intent;

public class MbnApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        startService(new Intent(this, MbnOverrideService.class));
    }
}
