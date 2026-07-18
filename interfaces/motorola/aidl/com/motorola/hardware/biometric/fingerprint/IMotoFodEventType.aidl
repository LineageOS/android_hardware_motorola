/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

@VintfStability
@Backing(type="int")
enum IMotoFodEventType {
    FINGER_DOWN = 0,
    FINGER_UP = 1,
    CANCEL = 2,
    EXTEND_1 = 3,
    EXTEND_2 = 4,
    UNKNOWN = 5,
}
