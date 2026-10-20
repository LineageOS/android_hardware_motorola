/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.internal.app;

import com.motorola.internal.app.IMotoFaceServiceReceiver;

/**
 * Re-creation of Motorola's framework.jar interface (stock rtwo, dumped with tools/dumpclasses.py).
 * Method ORDER defines the transaction codes and must not change:
 *   setCallback=1 enroll=2 authenticate=3 cancel=4 remove=5 resetLockout=6 setFeature=7
 *   enumerate=8 getFeatureCount=9 generateChallenge=10 revokeChallenge=11 getAuthenticatorId=12 getFeature=13
 * Stock declares @EnforcePermission on every method; it is intentionally omitted here (the APK's
 * service is already protected by android.permission.MANAGE_BIOMETRIC in its manifest).
 */
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
