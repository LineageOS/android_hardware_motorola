/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

import com.motorola.hardware.biometric.fingerprint.IMotoEventResult;

@VintfStability
interface IMotoCaptiveSensorTestCallback {
    oneway void onCheckerboardTestResult(IMotoEventResult result);
    oneway void onGetSensorInfoResult(IMotoEventResult result, in @nullable String info);
    oneway void onImageQualityTestResult(IMotoEventResult result);
    oneway void onOtpvalidationTestResult(IMotoEventResult result);
    oneway void onSelfTestResult(IMotoEventResult result);
}
