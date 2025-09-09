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

    private val packageContext = context.createPackageContext(
        KeyHandler::class.java.getPackage()!!.name, 0
    )

    private val eventHandler = EventHandler(packageContext)

    override fun handleKeyEvent(event: KeyEvent): KeyEvent? {
        if (event.action != KeyEvent.ACTION_DOWN) return event

        val scanCode = event.scanCode

        return when (scanCode) {
            PEN_REMOVED -> {
                Log.d(TAG, "Stylus removed: $scanCode")
                eventHandler.handleRemoved()
                null
            }
            PEN_INSERTED -> {
                Log.d(TAG, "Stylus inserted: $scanCode")
                eventHandler.handleInserted()
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
