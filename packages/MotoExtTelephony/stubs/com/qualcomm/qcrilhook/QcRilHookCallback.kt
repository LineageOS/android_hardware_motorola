/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.qualcomm.qcrilhook

interface QcRilHookCallback {
    fun onQcRilHookReady()

    fun onQcRilHookDisconnected()
}
