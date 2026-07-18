/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto_aidl"

#include <aidl/com/motorola/hardware/biometric/fingerprint/IMotoFingerPrint.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>

#include <chrono>
#include <mutex>
#include <thread>

#include "UdfpsHandlerMoto.h"

using ::aidl::com::motorola::hardware::biometric::fingerprint::IMotoEventResult;
using ::aidl::com::motorola::hardware::biometric::fingerprint::IMotoFingerPrint;
using ::aidl::com::motorola::hardware::biometric::fingerprint::IMotoFodEventType;

namespace {
constexpr const char* kMotoFpService =
        "com.motorola.hardware.biometric.fingerprint.IMotoFingerPrint/default";
}  // namespace

class MotoAidlUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* /*device*/) {
        mHbmFodEnabled = false;

        ndk::SpAIBinder binder(AServiceManager_waitForService(kMotoFpService));
        mMotoFingerprint = IMotoFingerPrint::fromBinder(binder);
        if (mMotoFingerprint == nullptr) {
            LOG(ERROR) << "Failed to get IMotoFingerPrint/default";
        }
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        if (SCREEN_WAKE_DELAY > 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(SCREEN_WAKE_DELAY));
        }
        enableHighBrightFod();
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
            onFingerUp();
        }).detach();
    }

    void onFingerUp() { disableHighBrightFod(); }

    void onAcquired(int32_t result, int32_t /*vendorCode*/) {
        if (result == FINGERPRINT_ACQUIRED_GOOD) {
            // Set finger as up to disable HBM already, even if the finger is
            // still pressed.
            onFingerUp();
        }
    }

    void cancel() { disableHighBrightFod(); }

  private:
    void sendFodEvent(IMotoFodEventType event) {
        if (mMotoFingerprint == nullptr) {
            return;
        }
        IMotoEventResult result;
        auto status = mMotoFingerprint->sendFodEvent(event, std::nullopt, &result);
        if (!status.isOk()) {
            LOG(ERROR) << "sendFodEvent(" << static_cast<int>(event)
                       << ") failed: " << status.getDescription();
        }
    }

    void disableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (!mHbmFodEnabled) {
            return;
        }

        sendFodEvent(IMotoFodEventType::FINGER_UP);
        setHbmState(OFF);

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        setHbmState(ON);
        sendFodEvent(IMotoFodEventType::FINGER_DOWN);

        mHbmFodEnabled = true;
    }

    bool mHbmFodEnabled;
    std::mutex mSetHbmFodMutex;

    std::shared_ptr<IMotoFingerPrint> mMotoFingerprint;
};

static UdfpsHandler* create() {
    return new MotoAidlUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
