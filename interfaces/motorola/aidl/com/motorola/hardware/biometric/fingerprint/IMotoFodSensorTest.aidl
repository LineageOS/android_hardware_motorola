/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.fingerprint;

import com.motorola.hardware.biometric.fingerprint.IMotoFodSensorTestCallback;
import com.motorola.hardware.biometric.fingerprint.TestResult;

@VintfStability
interface IMotoFodSensorTest {
    int beginSensorTest(in IMotoFodSensorTestCallback callback);
    void finishSensorTest(int cookie);
    void selfTest();
    void imagequalityTest();
    void checkCalibrationStatus();
    String[] getCalibrationSteps();
    void performCalibrationStep(int step);
    TestResult getCalibrationResult();
}
