/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

import com.motorola.hardware.biometric.fingerprint.IMotoEventResult;
import com.motorola.hardware.biometric.fingerprint.TestResult;

@VintfStability
interface IMotoFodSensorTestCallback {
    /** txn 1 */
    oneway void onSelfTestResult(in IMotoEventResult result, in TestResult testResult);

    /** txn 2 */
    oneway void onImageQualityTestResult(in IMotoEventResult result, in int quality);

    /** txn 3 */
    oneway void onCheckCalibrationStatusResult(in IMotoEventResult result);

    /** txn 4 */
    oneway void onCalibrationStepTestResult(in IMotoEventResult result);
}
