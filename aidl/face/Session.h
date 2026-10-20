/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/android/hardware/biometrics/common/BnCancellationSignal.h>
#include <aidl/android/hardware/biometrics/face/BnSession.h>
#include <aidl/android/hardware/biometrics/face/ISessionCallback.h>
#include <aidl/android/hardware/common/NativeHandle.h>

#include <functional>
#include <mutex>
#include <vector>

namespace aidl::android::hardware::biometrics::face {

class Session : public BnSession {
  public:
    Session(int32_t sensorId, int32_t userId, std::shared_ptr<ISessionCallback> cb);
    ~Session() override;

    // ---- ISession ----
    ndk::ScopedAStatus generateChallenge() override;
    ndk::ScopedAStatus revokeChallenge(int64_t challenge) override;
    ndk::ScopedAStatus getEnrollmentConfig(EnrollmentType,
                                           std::vector<EnrollmentStageConfig>* out) override;
    ndk::ScopedAStatus enroll(
            const keymaster::HardwareAuthToken& hat, EnrollmentType type,
            const std::vector<Feature>& features,
            const std::optional<::aidl::android::hardware::common::NativeHandle>& previewSurface,
            std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus authenticate(int64_t operationId,
                                    std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus detectInteraction(
            std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus enumerateEnrollments() override;
    ndk::ScopedAStatus removeEnrollments(const std::vector<int32_t>& ids) override;
    ndk::ScopedAStatus getFeatures() override;
    ndk::ScopedAStatus setFeature(const keymaster::HardwareAuthToken& hat, Feature feature,
                                  bool enabled) override;
    ndk::ScopedAStatus getAuthenticatorId() override;
    ndk::ScopedAStatus invalidateAuthenticatorId() override;
    ndk::ScopedAStatus resetLockout(const keymaster::HardwareAuthToken& hat) override;
    ndk::ScopedAStatus close() override;
    ndk::ScopedAStatus authenticateWithContext(
            int64_t operationId, const common::OperationContext&,
            std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus enrollWithContext(
            const keymaster::HardwareAuthToken& hat, EnrollmentType type,
            const std::vector<Feature>& features,
            const std::optional<::aidl::android::hardware::common::NativeHandle>& previewSurface,
            const common::OperationContext&,
            std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus detectInteractionWithContext(
            const common::OperationContext&,
            std::shared_ptr<common::ICancellationSignal>* out) override;
    ndk::ScopedAStatus onContextChanged(const common::OperationContext&) override;
    // V5: FaceEnrollOptions.h exists, so ISession has enrollWithOptions (no `override` on
    // purpose; if the compiler rejects the field names, check FaceEnrollOptions.h).
    ndk::ScopedAStatus enrollWithOptions(const FaceEnrollOptions& options,
                                         std::shared_ptr<common::ICancellationSignal>* out);

    // ---- events from the bridge app (called by BridgeHost) ----
    void onChallengeGenerated(int64_t challenge);
    void onChallengeRevoked();
    void onEnrollResult(int32_t faceId, int32_t userId, int32_t remaining);
    void onAuthenticated(int32_t faceId, int32_t userId);
    void onMotoError(int32_t motoError, int32_t vendorCode);
    void onRemoved(const std::vector<int32_t>& ids, int32_t userId);
    void onEnumerate(const std::vector<int32_t>& ids, int32_t userId);
    void onFeature(int32_t feature, bool enabled);
    void onLockoutCleared();
    void onBridgeLost();

  private:
    enum class Op { NONE, ENROLL, AUTH, OTHER };

    bool beginOp(Op op);  // false -> already reported HW_UNAVAILABLE / busy
    void endOp();
    void reportError(Error e, int32_t vendor = 0);
    std::shared_ptr<common::ICancellationSignal> makeCancel();

    const int32_t mSensorId;
    const int32_t mUserId;
    std::shared_ptr<ISessionCallback> mCb;

    std::mutex mLock;
    Op mOp = Op::NONE;
    int32_t mEnrollmentId = 0;        // last known Moto faceId (0 = none)
    std::vector<int32_t> mRemoveAcc;  // ids removed so far
    size_t mRemovePending = 0;
    enum class FeatMode { NONE, GET, SET };
    FeatMode mFeatMode = FeatMode::NONE;
    size_t mFeatPending = 0;
    std::vector<Feature> mFeatAcc;
    Feature mFeatSet = Feature::REQUIRE_ATTENTION;
};

}  // namespace aidl::android::hardware::biometrics::face
