/*
 * Copyright (C) 2017 The Android Open Source Project
 *               2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "android.hardware.biometrics.fingerprint@2.3-service.moto"

#include <hardware/hw_auth_token.h>

#include <hardware/hardware.h>
#include "BiometricsFingerprint.h"

#include <inttypes.h>
#include <unistd.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_3 {
namespace implementation {

BiometricsFingerprint::BiometricsFingerprint() {
    mService = IBiometricsFingerprint_2_1::getService();
}

Return<uint64_t> BiometricsFingerprint::setNotify(
        const sp<IBiometricsFingerprintClientCallback>& clientCallback) {
    return mService->setNotify(clientCallback);
}

Return<uint64_t> BiometricsFingerprint::preEnroll() {
    return mService->preEnroll();
}

Return<RequestStatus> BiometricsFingerprint::enroll(const hidl_array<uint8_t, 69>& hat,
                                                    uint32_t gid, uint32_t timeoutSec) {
    return mService->enroll(hat, gid, timeoutSec);
}

Return<RequestStatus> BiometricsFingerprint::postEnroll() {
    return mService->postEnroll();
}

Return<uint64_t> BiometricsFingerprint::getAuthenticatorId() {
    return mService->getAuthenticatorId();
}

Return<RequestStatus> BiometricsFingerprint::cancel() {
    return mService->cancel();
}

Return<RequestStatus> BiometricsFingerprint::enumerate() {
    return mService->enumerate();
}

Return<RequestStatus> BiometricsFingerprint::remove(uint32_t gid, uint32_t fid) {
    return mService->remove(gid, fid);
}

Return<RequestStatus> BiometricsFingerprint::setActiveGroup(uint32_t gid,
                                                            const hidl_string& storePath) {
    return mService->setActiveGroup(gid, storePath);
}

Return<RequestStatus> BiometricsFingerprint::authenticate(uint64_t operationId, uint32_t gid) {
    return mService->authenticate(operationId, gid);
}

Return<bool> BiometricsFingerprint::isUdfps(uint32_t /*sensorId*/) {
    return false;
}

Return<void> BiometricsFingerprint::onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/,
                                                 float /*major*/) {
    return Void();
}

Return<void> BiometricsFingerprint::onFingerUp() {
    return Void();
}

}  // namespace implementation
}  // namespace V2_3
}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace android
