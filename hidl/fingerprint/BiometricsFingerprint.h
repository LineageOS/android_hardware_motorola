/*
 * Copyright (C) 2017 The Android Open Source Project
 *               2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/hardware/biometrics/fingerprint/2.2/IBiometricsFingerprintClientCallback.h>
#include <android/hardware/biometrics/fingerprint/2.2/types.h>
#include <android/hardware/biometrics/fingerprint/2.3/IBiometricsFingerprint.h>
#include <android/log.h>
#include <hardware/hardware.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <log/log.h>

#include "UdfpsHandler.h"

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_3 {
namespace implementation {

using ::android::sp;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError;
using ::android::hardware::biometrics::fingerprint::V2_1::RequestStatus;
using ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo;
using ::android::hardware::biometrics::fingerprint::V2_2::IBiometricsFingerprintClientCallback;
using ::android::hardware::biometrics::fingerprint::V2_3::IBiometricsFingerprint;

struct BiometricsFingerprint : public IBiometricsFingerprint,
                               public IBiometricsFingerprintClientCallback {
  public:
    BiometricsFingerprint();
    ~BiometricsFingerprint();

    // Methods from ::android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprint
    // follow.
    Return<uint64_t> setNotify(
            const sp<V2_1::IBiometricsFingerprintClientCallback>& clientCallback) override;
    Return<uint64_t> preEnroll() override;
    Return<RequestStatus> enroll(const hidl_array<uint8_t, 69>& hat, uint32_t gid,
                                 uint32_t timeoutSec) override;
    Return<RequestStatus> postEnroll() override;
    Return<uint64_t> getAuthenticatorId() override;
    Return<RequestStatus> cancel() override;
    Return<RequestStatus> enumerate() override;
    Return<RequestStatus> remove(uint32_t gid, uint32_t fid) override;
    Return<RequestStatus> setActiveGroup(uint32_t gid, const hidl_string& storePath) override;
    Return<RequestStatus> authenticate(uint64_t operationId, uint32_t gid) override;

    // Methods from ::android::hardware::biometrics::fingerprint::V2_3::IBiometricsFingerprint
    // follow.
    Return<bool> isUdfps(uint32_t sensorId) override;
    Return<void> onFingerDown(uint32_t x, uint32_t y, float minor, float major) override;
    Return<void> onFingerUp() override;

    // Methods from
    // ::android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprintClientCallback
    // follow.
    Return<void> onEnrollResult(uint64_t deviceId, uint32_t fingerId, uint32_t groupId,
                                uint32_t remaining) override;
    Return<void> onAcquired(uint64_t deviceId, V2_1::FingerprintAcquiredInfo acquiredInfo,
                            int32_t vendorCode) override;
    Return<void> onAuthenticated(uint64_t deviceId, uint32_t fingerId, uint32_t groupId,
                                 const hidl_vec<uint8_t>& token) override;
    Return<void> onError(uint64_t deviceId, FingerprintError error, int32_t vendorCode) override;
    Return<void> onRemoved(uint64_t deviceId, uint32_t fingerId, uint32_t groupId,
                           uint32_t remaining) override;
    Return<void> onEnumerate(uint64_t deviceId, uint32_t fingerId, uint32_t groupId,
                             uint32_t remaining) override;

    // Methods from
    // ::android::hardware::biometrics::fingerprint::V2_2::IBiometricsFingerprintClientCallback
    // follow.
    Return<void> onAcquired_2_2(uint64_t deviceId, FingerprintAcquiredInfo acquiredInfo,
                                int32_t vendorCode) override;

  private:
    sp<V2_1::IBiometricsFingerprint> mService;
    sp<V2_1::IBiometricsFingerprintClientCallback> mClientCallback;
    UdfpsHandlerFactory* mUdfpsHandlerFactory;
    UdfpsHandler* mUdfpsHandler;
};

}  // namespace implementation
}  // namespace V2_3
}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace android
