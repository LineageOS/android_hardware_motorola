/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.internal.app;

/**
 * Transaction codes: onEnrollResult=1 onAuthenticated=2 onAcquired=3 onError=4
 * onRemoved=5 onEnumerate=6 onLockoutChanged=7 (order matters).
 */
oneway interface IMotoFaceServiceReceiver {
    void onEnrollResult(int faceId, int userId, int remaining);
    void onAuthenticated(int faceId, int userId, in byte[] token);
    void onAcquired(int arg0, int arg1, int arg2);
    void onError(int error, int vendorCode);
    void onRemoved(in int[] faceIds, int userId);
    void onEnumerate(in int[] faceIds, int userId);
    void onLockoutChanged(long durationMs);
}
