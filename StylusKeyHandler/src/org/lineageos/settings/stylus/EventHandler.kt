/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.stylus

import android.content.Context

class EventHandler(private val packageContext: Context) {

    private val utils = Utils(packageContext)

    private val sharedPreferences
        get() = packageContext.getSharedPreferences(
            packageContext.packageName + "_preferences",
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
        )

    fun handleRemoved() {
        if (sharedPreferences.getBoolean(KEY_VIBRATE, true)) {
            utils.vibrateIfNeeded(Utils.VIBRATE_HEAVY_CLICK)
        }
        utils.turnScreenOn()
        val packageName = sharedPreferences.getString(KEY_LAUNCH_APP, null)
        if (packageName != null) {
            utils.launchApp(packageName)
        }
    }

    fun handleInserted() {
        if (sharedPreferences.getBoolean(KEY_VIBRATE, true)) {
            utils.vibrateIfNeeded(Utils.VIBRATE_HEAVY_CLICK)
        }
    }

    companion object {
        const val KEY_VIBRATE = "vibrate"
        const val KEY_LAUNCH_APP = "launch_app"
    }
}
