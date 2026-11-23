/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.mbnoverride

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MbnOverrideBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        context.startService(Intent(context, MbnOverrideService::class.java))
    }
}
