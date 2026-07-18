/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

import com.motorola.hardware.biometric.fingerprint.FingerHardWareInfo;
import com.motorola.hardware.biometric.fingerprint.IMotoEventResult;
import com.motorola.hardware.biometric.fingerprint.IMotoFodEventType;

@VintfStability
interface IMotoFingerPrint {
    /** txn 1 */
    String getCheckinVersion();

    /** txn 2 */
    FingerHardWareInfo getHardwareInfo();

    /** txn 3 */
    String[] getUnlockPerformanceData();

    /** txn 4 - the FOD entry point used by the UDFPS handler. */
    IMotoEventResult sendFodEvent(in IMotoFodEventType eventType, in @nullable byte[] eventId);
}
