/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.qualcomm.qcrilhook

import android.content.Context

/**
 * Compile-time stub. Only the signatures used by MbnOverride are declared; they match the QcRilHook
 * class of the device's /system_ext/framework/qcrilhook.jar, which provides the real implementation
 * at runtime.
 */
open class QcRilHook(context: Context?, callback: QcRilHookCallback?) {

    open fun qcRilGetAllConfigs(): Boolean {
        return false
    }

    open fun qcRilGetConfig(phoneId: Int): String? {
        return null
    }

    open fun qcRilGetMetaInfoForConfig(configId: String?, phoneId: Int): String? {
        return null
    }

    open fun qcRilSelectConfig(configId: String?, sub: Int): Boolean {
        return false
    }

    open fun qcRilActivateConfig(sub: Int): Boolean {
        return false
    }
}
