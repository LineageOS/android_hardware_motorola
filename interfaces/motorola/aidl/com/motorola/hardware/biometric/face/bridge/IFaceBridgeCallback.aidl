/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.face.bridge;

/** App -> HAL events, mirroring motorola.core_services.face.MotoFaceServiceReceiver. */
@VintfStability
oneway interface IFaceBridgeCallback {
    void onChallengeGenerated(long challenge);
    void onChallengeRevoked();
    void onEnrollResult(int faceId, int userId, int remaining);
    void onAuthenticated(int faceId, int userId);
    /** error uses android.hardware.face.FaceManager.FACE_ERROR_* numbering. */
    void onError(int error, int vendorCode);
    void onRemoved(in int[] faceIds, int userId);
    void onEnumerate(in int[] faceIds, int userId);
    void onFeature(int feature, boolean enabled);
    void onLockoutCleared();
}
