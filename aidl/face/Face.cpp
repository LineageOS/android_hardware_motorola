/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "face-moto"

#include "Face.h"

#include <android-base/logging.h>

#include "BridgeHost.h"
#include "Session.h"

namespace aidl::android::hardware::biometrics::face {

namespace {
// BiometricService rejects two authenticators with the same sensor id, across ALL modalities
// ("IllegalStateException: Cannot register duplicate authenticator" -> system_server crash loop).
// The fingerprint sensor(s) use 0, so the face sensor needs its own id.
constexpr int32_t kFaceSensorId = 10;
}  // namespace

ndk::ScopedAStatus Face::getSensorProps(std::vector<SensorProps>* out) {
    SensorProps p;
    p.commonProps.sensorId = kFaceSensorId;
    // 2D RGB matching is spoofable: advertise it as CONVENIENCE (keyguard only, never
    // bound to keystore / BiometricPrompt). Do NOT raise this without a secure pipeline.
    p.commonProps.sensorStrength = common::SensorStrength::CONVENIENCE;
    p.commonProps.maxEnrollmentsPerUser = 1;
    p.sensorType = FaceSensorType::RGB;
    // The Motorola APK owns the camera and its enrollment UI/preview.
    p.halControlsPreview = false;
    p.supportsDetectInteraction = false;
    out->push_back(std::move(p));
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Face::createSession(int32_t sensorId, int32_t userId,
                                       const std::shared_ptr<ISessionCallback>& cb,
                                       std::shared_ptr<ISession>* out) {
    LOG(INFO) << "createSession sensor=" << sensorId << " user=" << userId;
    auto s = ndk::SharedRefBase::make<Session>(sensorId, userId, cb);
    *out = s;
    return ndk::ScopedAStatus::ok();
}

}  // namespace aidl::android::hardware::biometrics::face
