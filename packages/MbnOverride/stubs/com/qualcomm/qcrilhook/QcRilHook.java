/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.qualcomm.qcrilhook;

import android.content.Context;

public class QcRilHook {

    public QcRilHook(Context context, QcRilHookCallback callback) {
    }

    public boolean qcRilGetAllConfigs() {
        return false;
    }

    public String qcRilGetConfig(int phoneId) {
        return null;
    }

    public String qcRilGetMetaInfoForConfig(String configId, int phoneId) {
        return null;
    }

    public boolean qcRilSelectConfig(String configId, int sub) {
        return false;
    }

    public boolean qcRilActivateConfig(int sub) {
        return false;
    }
}
