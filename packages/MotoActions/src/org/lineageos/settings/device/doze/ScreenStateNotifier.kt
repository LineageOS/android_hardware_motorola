/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.doze

interface ScreenStateNotifier {
    fun onScreenStateChanged(screenOn: Boolean)
}
