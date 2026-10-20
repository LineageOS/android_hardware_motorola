/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "face-moto-host"

#include "BridgeHost.h"

#include <android-base/logging.h>

#include "Session.h"

namespace com::motorola::hardware::biometric::face {

using aidl::android::hardware::biometrics::face::Session;

std::shared_ptr<BridgeHost> BridgeHost::get() {
    static std::shared_ptr<BridgeHost> inst = ndk::SharedRefBase::make<BridgeHost>();
    return inst;
}

void BridgeHost::onBridgeDied(void* cookie) {
    auto* self = static_cast<BridgeHost*>(cookie);
    LOG(ERROR) << "face bridge app died";
    Session* s;
    {
        std::lock_guard<std::mutex> g(self->mLock);
        self->mBridge.reset();
        self->mBridgeBinder.set(nullptr);
        s = self->mSession;
    }
    if (s) s->onBridgeLost();
}

ndk::ScopedAStatus BridgeHost::registerBridge(const std::shared_ptr<IFaceBridge>& bridge) {
    std::lock_guard<std::mutex> g(mLock);
    if (!mDeath) {
        mDeath = AIBinder_DeathRecipient_new(&BridgeHost::onBridgeDied);
    }
    mBridge = bridge;
    mBridgeBinder = bridge->asBinder();
    AIBinder_linkToDeath(mBridgeBinder.get(), mDeath, this);
    if (!mCallback) mCallback = ndk::SharedRefBase::make<BridgeCallback>(this);
    bridge->setCallback(mCallback);
    LOG(INFO) << "face bridge registered";
    return ndk::ScopedAStatus::ok();
}

std::shared_ptr<IFaceBridge> BridgeHost::bridge() {
    std::lock_guard<std::mutex> g(mLock);
    return mBridge;
}

void BridgeHost::setSession(Session* s) {
    std::lock_guard<std::mutex> g(mLock);
    mSession = s;
}
void BridgeHost::clearSession(Session* s) {
    std::lock_guard<std::mutex> g(mLock);
    if (mSession == s) mSession = nullptr;
}

template <typename F>
void BridgeHost::withSession(F&& f) {
    Session* s;
    {
        std::lock_guard<std::mutex> g(mLock);
        s = mSession;
    }
    // Called without mLock held so Session may call bridge() from its handlers.
    // TODO: a Session destroyed concurrently with an in-flight event is a (small) race.
    if (s)
        f(*s);
    else
        LOG(WARNING) << "bridge event with no active session";
}

#define FWD(call)                                    \
    mHost->withSession([&](Session& s) { s.call; }); \
    return ndk::ScopedAStatus::ok();

ndk::ScopedAStatus BridgeCallback::onChallengeGenerated(int64_t c){
        FWD(onChallengeGenerated(c))} ndk::ScopedAStatus BridgeCallback::onChallengeRevoked(){
        FWD(onChallengeRevoked())} ndk::ScopedAStatus BridgeCallback::onEnrollResult(int32_t f,
                                                                                     int32_t u,
                                                                                     int32_t r){
        FWD(onEnrollResult(f, u, r))} ndk::ScopedAStatus BridgeCallback::onAuthenticated(int32_t f,
                                                                                         int32_t u){
        FWD(onAuthenticated(f, u))} ndk::ScopedAStatus BridgeCallback::onError(int32_t e,
                                                                               int32_t v){
        FWD(onMotoError(e, v))} ndk::ScopedAStatus
        BridgeCallback::onRemoved(const std::vector<int32_t>& ids,
                                  int32_t u){FWD(onRemoved(ids, u))} ndk::ScopedAStatus
        BridgeCallback::onEnumerate(const std::vector<int32_t>& ids, int32_t u){
                FWD(onEnumerate(ids, u))} ndk::ScopedAStatus BridgeCallback::onFeature(int32_t f,
                                                                                       bool e){
                FWD(onFeature(f, e))} ndk::ScopedAStatus BridgeCallback::onLockoutCleared() {
    FWD(onLockoutCleared())
}

}  // namespace com::motorola::hardware::biometric::face
