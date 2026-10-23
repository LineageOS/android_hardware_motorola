/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Starting")
        context.startService(Intent(context, NrEnablerService::class.java))

        val enabled = context.resources.getBoolean(R.bool.config_enableLegacySimSwitch)
        runCatching {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context, SimSwitchActivity::class.java),
                    if (enabled) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
                Log.d(TAG, "SimSwitchActivity enabled=$enabled")
            }
            .onFailure { Log.e(TAG, "Failed to set SimSwitchActivity state", it) }
    }

    companion object {
        private const val TAG = "MotoExtTelephony"
    }
}
