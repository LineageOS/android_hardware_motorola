/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.face.bridge;

import com.motorola.hardware.biometric.face.bridge.IFaceBridgeCallback;

/**
 * HAL -> app requests. Everything is oneway; results come back through
 * IFaceBridgeCallback so no binder thread is ever blocked on the other side.
 * Methods mirror motorola.core_services.face.MotoFaceService.
 */
@VintfStability
oneway interface IFaceBridge {
    void setCallback(IFaceBridgeCallback cb);
    void generateChallenge(int timeoutSec);
    void revokeChallenge();
    /** hat is a serialized hw_auth_token_t (69 bytes). */
    void enroll(in byte[] hat, int timeoutSec, in int[] disabledFeatures);
    void authenticate(long operationId);
    void cancel();
    void enumerate();
    void remove(int faceId);
    void resetLockout(in byte[] hat);
    void getFeature(int feature, int faceId);
    void setFeature(int feature, boolean enabled, in byte[] hat, int faceId);
}
