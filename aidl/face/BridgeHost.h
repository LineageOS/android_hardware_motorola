/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/com/motorola/hardware/biometric/face/bridge/BnFaceBridgeCallback.h>
#include <aidl/com/motorola/hardware/biometric/face/bridge/BnFaceBridgeHost.h>
#include <aidl/com/motorola/hardware/biometric/face/bridge/IFaceBridge.h>

#include <functional>
#include <memory>
#include <mutex>

namespace aidl::android::hardware::biometrics::face {
class Session;
}

namespace com::motorola::hardware::biometric::face {

using aidl::com::motorola::hardware::biometric::face::bridge::IFaceBridge;
using aidl::com::motorola::hardware::biometric::face::bridge::IFaceBridgeCallback;

/**
 * Process-wide link to the Java bridge app. Holds the registered IFaceBridge and
 * forwards its callbacks to whichever Session is currently active.
 */
class BridgeHost;

/** Receives app -> HAL events and forwards them to the active Session. */
class BridgeCallback
    : public aidl::com::motorola::hardware::biometric::face::bridge::BnFaceBridgeCallback {
  public:
    explicit BridgeCallback(BridgeHost* host) : mHost(host) {}
    ndk::ScopedAStatus onChallengeGenerated(int64_t challenge) override;
    ndk::ScopedAStatus onChallengeRevoked() override;
    ndk::ScopedAStatus onEnrollResult(int32_t faceId, int32_t userId, int32_t remaining) override;
    ndk::ScopedAStatus onAuthenticated(int32_t faceId, int32_t userId) override;
    ndk::ScopedAStatus onError(int32_t error, int32_t vendorCode) override;
    ndk::ScopedAStatus onRemoved(const std::vector<int32_t>& faceIds, int32_t userId) override;
    ndk::ScopedAStatus onEnumerate(const std::vector<int32_t>& faceIds, int32_t userId) override;
    ndk::ScopedAStatus onFeature(int32_t feature, bool enabled) override;
    ndk::ScopedAStatus onLockoutCleared() override;

  private:
    BridgeHost* mHost;
};

class BridgeHost : public aidl::com::motorola::hardware::biometric::face::bridge::BnFaceBridgeHost {
  public:
    static std::shared_ptr<BridgeHost> get();

    ndk::ScopedAStatus registerBridge(const std::shared_ptr<IFaceBridge>& bridge) override;

    // Returns nullptr while no bridge app is connected.
    std::shared_ptr<IFaceBridge> bridge();

    void setSession(aidl::android::hardware::biometrics::face::Session* s);
    void clearSession(aidl::android::hardware::biometrics::face::Session* s);

    template <typename F>
    void withSession(F&& f);

  private:
    static void onBridgeDied(void* cookie);
    std::shared_ptr<BridgeCallback> mCallback;

    std::mutex mLock;
    std::shared_ptr<IFaceBridge> mBridge;
    ndk::SpAIBinder mBridgeBinder;
    AIBinder_DeathRecipient* mDeath = nullptr;
    aidl::android::hardware::biometrics::face::Session* mSession = nullptr;
};

}  // namespace com::motorola::hardware::biometric::face
