/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.mbnoverride

import android.app.Application
import android.content.Intent

class MbnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startService(Intent(this, MbnOverrideService::class.java))
    }
}
