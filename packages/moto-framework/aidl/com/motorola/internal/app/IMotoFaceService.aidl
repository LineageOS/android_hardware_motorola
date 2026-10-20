/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.internal.app;

import com.motorola.internal.app.IMotoFaceServiceReceiver;

interface IMotoFaceService {
    void setCallback(IMotoFaceServiceReceiver receiver);
    void enroll(in byte[] hat, int timeoutSec, in int[] disabledFeatures);
    void authenticate(long operationId);
    void cancel();
    void remove(int faceId);
    void resetLockout(in byte[] hat);
    void setFeature(int feature, boolean enabled, in byte[] hat, int faceId);
    int enumerate();
    int getFeatureCount();
    long generateChallenge(int timeoutSec);
    int revokeChallenge();
    int getAuthenticatorId();
    boolean getFeature(int feature, int faceId);
}
