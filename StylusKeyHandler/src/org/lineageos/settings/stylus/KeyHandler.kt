/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.stylus

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import com.android.internal.os.DeviceKeyHandler

class KeyHandler(context: Context) : DeviceKeyHandler {

    private val utils = Utils(context)

    override fun handleKeyEvent(event: KeyEvent): KeyEvent? {
        if (event.action != KeyEvent.ACTION_DOWN) return event

        val scanCode = event.scanCode

        return when (scanCode) {
            PEN_REMOVED -> {
                Log.d(TAG, "Stylus removed: $scanCode")
                utils.vibrateIfNeeded(Utils.VIBRATE_HEAVY_CLICK, false)
                utils.turnScreenOn()
                utils.launchApp()
                null
            }
            PEN_INSERTED -> {
                Log.d(TAG, "Stylus inserted: $scanCode")
                utils.vibrateIfNeeded(Utils.VIBRATE_HEAVY_CLICK, true)
                null
            }
            else -> event
        }
    }

    companion object {
        private const val TAG = "KeyHandler"

        private const val PEN_REMOVED = 601
        private const val PEN_INSERTED = 602
    }
}
