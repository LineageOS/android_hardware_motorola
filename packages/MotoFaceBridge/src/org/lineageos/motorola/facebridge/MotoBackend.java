/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
 
package org.lineageos.motorola.facebridge;

/**
 * Transport to Motorola's FaceAuthService (motorola.core_services.face.MotoFaceService).
 * Mirrors its 13 methods; results arrive through {@link Events}.
 */
interface MotoBackend {
    interface Events {
        void onEnrollResult(int faceId, int userId, int remaining);
        void onAuthenticated(int faceId, int userId, byte[] token);
        void onError(int error, int vendorCode);
        void onRemoved(int[] faceIds, int userId);
        void onEnumerate(int[] faceIds, int userId);
    }

    void setEvents(Events events);

    long generateChallenge(int timeoutSec) throws Exception;
    int revokeChallenge() throws Exception;
    void enroll(byte[] hat, int timeoutSec, int[] disabledFeatures) throws Exception;
    void authenticate(long operationId) throws Exception;
    void cancel() throws Exception;
    int enumerate() throws Exception;
    void remove(int faceId) throws Exception;
    void resetLockout(byte[] hat) throws Exception;
    boolean getFeature(int feature, int faceId) throws Exception;
    void setFeature(int feature, boolean enabled, byte[] hat, int faceId) throws Exception;
}
