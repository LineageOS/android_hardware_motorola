/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

@VintfStability
parcelable TestResult {
    int result;
    String[] keys;
    int[] values;
    int status;
}
