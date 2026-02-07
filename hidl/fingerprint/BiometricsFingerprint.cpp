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
    mService = V2_1::IBiometricsFingerprint::getService();
}

Return<uint64_t> BiometricsFingerprint::setNotify(
        const sp<V2_1::IBiometricsFingerprintClientCallback>& clientCallback) {
    mClientCallback = std::move(clientCallback);
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

Return<void> BiometricsFingerprint::onEnrollResult(uint64_t deviceId, uint32_t fingerId,
                                                   uint32_t groupId, uint32_t remaining) {
    return mClientCallback->onEnrollResult(deviceId, fingerId, groupId, remaining);
}

Return<void> BiometricsFingerprint::onAcquired(uint64_t deviceId,
                                               V2_1::FingerprintAcquiredInfo acquiredInfo,
                                               int32_t vendorCode) {
    return mClientCallback->onAcquired(deviceId, acquiredInfo, vendorCode);
}

Return<void> BiometricsFingerprint::onAuthenticated(uint64_t deviceId, uint32_t fingerId,
                                                    uint32_t groupId,
                                                    const hidl_vec<uint8_t>& token) {
    return mClientCallback->onAuthenticated(deviceId, fingerId, groupId, token);
}

Return<void> BiometricsFingerprint::onError(uint64_t deviceId, FingerprintError error,
                                            int32_t vendorCode) {
    return mClientCallback->onError(deviceId, error, vendorCode);
}

Return<void> BiometricsFingerprint::onRemoved(uint64_t deviceId, uint32_t fingerId,
                                              uint32_t groupId, uint32_t remaining) {
    return mClientCallback->onRemoved(deviceId, fingerId, groupId, remaining);
}

Return<void> BiometricsFingerprint::onEnumerate(uint64_t deviceId, uint32_t fingerId,
                                                uint32_t groupId, uint32_t remaining) {
    return mClientCallback->onEnumerate(deviceId, fingerId, groupId, remaining);
}

Return<void> BiometricsFingerprint::onAcquired_2_2(uint64_t deviceId,
                                                   FingerprintAcquiredInfo acquiredInfo,
                                                   int32_t vendorCode) {
    return reinterpret_cast<V2_2::IBiometricsFingerprintClientCallback*>(mClientCallback.get())
            ->onAcquired_2_2(deviceId, acquiredInfo, vendorCode);
}

}  // namespace implementation
}  // namespace V2_3
}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace android
