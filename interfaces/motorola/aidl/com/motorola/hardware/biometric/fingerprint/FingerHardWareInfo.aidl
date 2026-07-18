/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

@VintfStability
parcelable FingerHardWareInfo {
    int status;
    String sensorId;
    String moduleId;
    String firmwareVersion;
}
