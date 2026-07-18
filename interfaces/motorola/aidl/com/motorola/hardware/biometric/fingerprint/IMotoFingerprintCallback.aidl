/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

@VintfStability
interface IMotoFingerprintCallback {
    /** txn 1 */
    oneway void ipc_callback(int arg1, int arg2, int arg3, in byte[] data, int arg5);

    /** txn 2 */
    oneway void onDaemonMessage(long arg1, int arg2, int arg3, in byte[] data);
}
