/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

@VintfStability
interface IMotoFpsNavigationCallback {
    /** txn 1 */
    oneway void onMove(int arg1, int arg2, int arg3, int arg4, int arg5);

    /** txn 2 */
    oneway void onTap(int arg1, int arg2, int arg3, int arg4);
}
