/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

import com.motorola.hardware.biometric.fingerprint.IMotoFpsNavigationCallback;

@VintfStability
interface IMotoFpsNavigate {
    /** txn 1 */
    void registerFpsNavigationCallback(in IMotoFpsNavigationCallback callback);

    /** txn 2 */
    void unregisterFpsNavigationCallback(in IMotoFpsNavigationCallback callback);

    /** txn 3 */
    boolean setFpsNavigationMode(in int mode);

    /** txn 4 */
    boolean isFpsNavigationSupport(in int arg);

    /** txn 5 */
    int getFpsNavigationCapability(in int arg);

    /** txn 6 */
    int getFpsNavigationCurrentMode();
}
