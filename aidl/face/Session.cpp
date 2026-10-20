/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "face-moto-session"

#include "Session.h"

#include <android-base/logging.h>

#include "BridgeHost.h"
#include "HatUtils.h"

using com::motorola::hardware::biometric::face::BridgeHost;
using com::motorola::hardware::biometric::face::serializeHat;

namespace aidl::android::hardware::biometrics::face {

namespace {
// Moto reports errors with android.hardware.face.FaceManager.FACE_ERROR_* numbers.
// VERIFY against the values the APK actually emits (adb logcat -s FaceAuthService).
constexpr int MOTO_HW_UNAVAILABLE = 1, MOTO_UNABLE_TO_PROCESS = 2, MOTO_TIMEOUT = 3,
              MOTO_NO_SPACE = 4, MOTO_CANCELED = 5, MOTO_UNABLE_TO_REMOVE = 6, MOTO_LOCKOUT = 7,
              MOTO_VENDOR = 8, MOTO_LOCKOUT_PERMANENT = 9, MOTO_REENROLL = 16;
constexpr int64_t kTimedLockoutMs = 30000;
constexpr int kChallengeTimeoutSec = 300;
constexpr int kEnrollTimeoutSec = 60;

// AIDL Feature is 0-based, the legacy HIDL numbering Moto uses is 1-based.
int toMotoFeature(Feature f) {
    return static_cast<int>(f) + 1;
}
Feature fromMotoFeature(int f) {
    return static_cast<Feature>(f - 1);
}

class FnCancel : public common::BnCancellationSignal {
  public:
    explicit FnCancel(std::function<void()> fn) : mFn(std::move(fn)) {}
    ndk::ScopedAStatus cancel() override {
        mFn();
        return ndk::ScopedAStatus::ok();
    }

  private:
    std::function<void()> mFn;
};
}  // namespace

Session::Session(int32_t sensorId, int32_t userId, std::shared_ptr<ISessionCallback> cb)
    : mSensorId(sensorId), mUserId(userId), mCb(std::move(cb)) {
    BridgeHost::get()->setSession(this);
}

Session::~Session() {
    BridgeHost::get()->clearSession(this);
}

bool Session::beginOp(Op op) {
    if (!BridgeHost::get()->bridge()) {
        reportError(Error::HW_UNAVAILABLE);
        return false;
    }
    std::lock_guard<std::mutex> g(mLock);
    if (mOp != Op::NONE) {
        LOG(WARNING) << "operation started while another is active";
    }
    mOp = op;
    return true;
}

void Session::endOp() {
    std::lock_guard<std::mutex> g(mLock);
    mOp = Op::NONE;
}

void Session::reportError(Error e, int32_t vendor) {
    endOp();
    mCb->onError(e, vendor);
}

std::shared_ptr<common::ICancellationSignal> Session::makeCancel() {
    return ndk::SharedRefBase::make<FnCancel>([] {
        if (auto b = BridgeHost::get()->bridge()) b->cancel();
        // The bridge app synthesizes onError(CANCELED) if the APK does not.
    });
}

// ---------------------------------------------------------------- ISession

ndk::ScopedAStatus Session::generateChallenge() {
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    BridgeHost::get()->bridge()->generateChallenge(kChallengeTimeoutSec);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::revokeChallenge(int64_t) {
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    BridgeHost::get()->bridge()->revokeChallenge();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::getEnrollmentConfig(EnrollmentType,
                                                std::vector<EnrollmentStageConfig>* out) {
    out->clear();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::enroll(
        const keymaster::HardwareAuthToken& hat, EnrollmentType,
        const std::vector<Feature>& features,
        const std::optional<::aidl::android::hardware::common::NativeHandle>&,
        std::shared_ptr<common::ICancellationSignal>* out) {
    *out = makeCancel();
    if (!beginOp(Op::ENROLL)) return ndk::ScopedAStatus::ok();
    // AIDL lists features to ENABLE; Moto wants the ones to DISABLE.
    std::vector<int32_t> disabled;
    for (Feature f : {Feature::REQUIRE_ATTENTION, Feature::REQUIRE_DIVERSE_POSES}) {
        if (std::find(features.begin(), features.end(), f) == features.end())
            disabled.push_back(toMotoFeature(f));
    }
    auto bytes = serializeHat(hat);
    BridgeHost::get()->bridge()->enroll(std::vector<uint8_t>(bytes.begin(), bytes.end()),
                                        kEnrollTimeoutSec, disabled);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::authenticate(int64_t operationId,
                                         std::shared_ptr<common::ICancellationSignal>* out) {
    *out = makeCancel();
    if (!beginOp(Op::AUTH)) return ndk::ScopedAStatus::ok();
    BridgeHost::get()->bridge()->authenticate(operationId);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::detectInteraction(std::shared_ptr<common::ICancellationSignal>* out) {
    *out = makeCancel();
    reportError(Error::UNABLE_TO_PROCESS);  // supportsDetectInteraction == false
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::enumerateEnrollments() {
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    BridgeHost::get()->bridge()->enumerate();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::removeEnrollments(const std::vector<int32_t>& ids) {
    if (ids.empty()) {
        mCb->onEnrollmentsRemoved({});
        return ndk::ScopedAStatus::ok();
    }
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    {
        std::lock_guard<std::mutex> g(mLock);
        mRemoveAcc.clear();
        mRemovePending = ids.size();
    }
    for (int32_t id : ids) BridgeHost::get()->bridge()->remove(id);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::getFeatures() {
    int32_t id;
    {
        std::lock_guard<std::mutex> g(mLock);
        id = mEnrollmentId;
    }
    if (id == 0) {  // not enrolled
        reportError(Error::UNABLE_TO_PROCESS);
        return ndk::ScopedAStatus::ok();
    }
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    {
        std::lock_guard<std::mutex> g(mLock);
        mFeatMode = FeatMode::GET;
        mFeatPending = 2;
        mFeatAcc.clear();
    }
    for (Feature f : {Feature::REQUIRE_ATTENTION, Feature::REQUIRE_DIVERSE_POSES})
        BridgeHost::get()->bridge()->getFeature(toMotoFeature(f), id);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::setFeature(const keymaster::HardwareAuthToken& hat, Feature feature,
                                       bool enabled) {
    int32_t id;
    {
        std::lock_guard<std::mutex> g(mLock);
        id = mEnrollmentId;
    }
    if (id == 0) {
        reportError(Error::UNABLE_TO_PROCESS);
        return ndk::ScopedAStatus::ok();
    }
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    {
        std::lock_guard<std::mutex> g(mLock);
        mFeatMode = FeatMode::SET;
        mFeatSet = feature;
    }
    auto bytes = serializeHat(hat);
    BridgeHost::get()->bridge()->setFeature(toMotoFeature(feature), enabled,
                                            std::vector<uint8_t>(bytes.begin(), bytes.end()), id);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::getAuthenticatorId() {
    mCb->onAuthenticatorIdRetrieved(0);  // required to be 0 for non-STRONG sensors
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::invalidateAuthenticatorId() {
    mCb->onAuthenticatorIdInvalidated(0);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::resetLockout(const keymaster::HardwareAuthToken& hat) {
    if (!beginOp(Op::OTHER)) return ndk::ScopedAStatus::ok();
    auto bytes = serializeHat(hat);
    BridgeHost::get()->bridge()->resetLockout(std::vector<uint8_t>(bytes.begin(), bytes.end()));
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::close() {
    endOp();
    mCb->onSessionClosed();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Session::authenticateWithContext(
        int64_t id, const common::OperationContext&,
        std::shared_ptr<common::ICancellationSignal>* out) {
    return authenticate(id, out);
}
ndk::ScopedAStatus Session::enrollWithContext(
        const keymaster::HardwareAuthToken& hat, EnrollmentType type,
        const std::vector<Feature>& features,
        const std::optional<::aidl::android::hardware::common::NativeHandle>& surface,
        const common::OperationContext&, std::shared_ptr<common::ICancellationSignal>* out) {
    return enroll(hat, type, features, surface, out);
}
ndk::ScopedAStatus Session::enrollWithOptions(const FaceEnrollOptions& o,
                                              std::shared_ptr<common::ICancellationSignal>* out) {
    return enroll(o.hardwareAuthToken, o.enrollmentType, o.features, std::nullopt, out);
}
ndk::ScopedAStatus Session::detectInteractionWithContext(
        const common::OperationContext&, std::shared_ptr<common::ICancellationSignal>* out) {
    return detectInteraction(out);
}
ndk::ScopedAStatus Session::onContextChanged(const common::OperationContext&) {
    return ndk::ScopedAStatus::ok();
}

// ------------------------------------------------------- bridge -> framework

void Session::onChallengeGenerated(int64_t challenge) {
    endOp();
    mCb->onChallengeGenerated(challenge);
}

void Session::onChallengeRevoked() {
    endOp();
    mCb->onChallengeRevoked(0);
}

void Session::onEnrollResult(int32_t faceId, int32_t, int32_t remaining) {
    {
        std::lock_guard<std::mutex> g(mLock);
        if (faceId != 0) mEnrollmentId = faceId;
    }
    mCb->onEnrollmentProgress(faceId, remaining);
    if (remaining == 0) endOp();
}

void Session::onAuthenticated(int32_t faceId, int32_t) {
    endOp();
    if (faceId == 0) {  // legacy HIDL semantics: faceId 0 == rejected
        mCb->onAuthenticationFailed();
        return;
    }
    {
        std::lock_guard<std::mutex> g(mLock);
        mEnrollmentId = faceId;
    }
    keymaster::HardwareAuthToken emptyHat;  // must be empty for non-STRONG sensors
    mCb->onAuthenticationSucceeded(faceId, emptyHat);
}

void Session::onMotoError(int32_t err, int32_t vendor) {
    switch (err) {
        case MOTO_HW_UNAVAILABLE:
            reportError(Error::HW_UNAVAILABLE, vendor);
            break;
        case MOTO_UNABLE_TO_PROCESS:
            reportError(Error::UNABLE_TO_PROCESS, vendor);
            break;
        case MOTO_TIMEOUT:
            reportError(Error::TIMEOUT, vendor);
            break;
        case MOTO_NO_SPACE:
            reportError(Error::NO_SPACE, vendor);
            break;
        case MOTO_CANCELED:
            reportError(Error::CANCELED, vendor);
            break;
        case MOTO_UNABLE_TO_REMOVE:
            reportError(Error::UNABLE_TO_REMOVE, vendor);
            break;
        case MOTO_VENDOR:
            reportError(Error::VENDOR, vendor);
            break;
        case MOTO_REENROLL:
            reportError(Error::REENROLL_REQUIRED, vendor);
            break;
        case MOTO_LOCKOUT:
            endOp();
            mCb->onLockoutTimed(kTimedLockoutMs);
            break;
        case MOTO_LOCKOUT_PERMANENT:
            endOp();
            mCb->onLockoutPermanent();
            break;
        default:
            LOG(WARNING) << "unmapped Moto error " << err << "/" << vendor;
            reportError(Error::VENDOR, err);
    }
}

void Session::onRemoved(const std::vector<int32_t>& ids, int32_t) {
    std::vector<int32_t> done;
    {
        std::lock_guard<std::mutex> g(mLock);
        // Moto reports removed ids; id 0 / empty means "all" -> clear our cache.
        for (int32_t id : ids) {
            mRemoveAcc.push_back(id);
            if (id == mEnrollmentId || id == 0) mEnrollmentId = 0;
        }
        if (ids.empty()) mEnrollmentId = 0;
        if (mRemovePending > 0) mRemovePending--;
        if (mRemovePending > 0) return;
        done = mRemoveAcc;
        mRemoveAcc.clear();
        mOp = Op::NONE;
    }
    mCb->onEnrollmentsRemoved(done);
}

void Session::onEnumerate(const std::vector<int32_t>& ids, int32_t) {
    {
        std::lock_guard<std::mutex> g(mLock);
        mEnrollmentId = ids.empty() ? 0 : ids.front();
        mOp = Op::NONE;
    }
    mCb->onEnrollmentsEnumerated(ids);
}

void Session::onFeature(int32_t feature, bool enabled) {
    FeatMode mode;
    std::vector<Feature> acc;
    Feature setF;
    {
        std::lock_guard<std::mutex> g(mLock);
        mode = mFeatMode;
        setF = mFeatSet;
        if (mode == FeatMode::GET) {
            if (enabled) mFeatAcc.push_back(fromMotoFeature(feature));
            if (mFeatPending > 0) mFeatPending--;
            if (mFeatPending > 0) return;
            acc = mFeatAcc;
        }
        mFeatMode = FeatMode::NONE;
        mOp = Op::NONE;
    }
    if (mode == FeatMode::GET)
        mCb->onFeaturesRetrieved(acc);
    else if (mode == FeatMode::SET)
        mCb->onFeatureSet(setF);
}

void Session::onLockoutCleared() {
    endOp();
    mCb->onLockoutCleared();
}

void Session::onBridgeLost() {
    reportError(Error::HW_UNAVAILABLE);
}

}  // namespace aidl::android::hardware::biometrics::face
