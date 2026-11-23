/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.qualcomm.qcrilhook;

public interface QcRilHookCallback {
    void onQcRilHookReady();
    void onQcRilHookDisconnected();
}
