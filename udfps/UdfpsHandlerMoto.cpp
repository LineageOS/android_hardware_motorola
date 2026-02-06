/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto"

#include <com/motorola/hardware/biometric/fingerprint/1.0/IMotoFingerPrint.h>

#include <chrono>
#include <thread>

#include "UdfpsHandlerMoto.h"

using ::android::sp;
using ::android::hardware::hidl_vec;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotFodEventResult;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotFodEventType;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotoFingerPrint;

class MotoUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* /*device*/) {
        mMotoFingerprint = IMotoFingerPrint::getService();
        mHbmFodEnabled = false;
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
            // Set finger as up to disable HBM already, even if the finger is still pressed
            onFingerUp();
        }
    }

    void cancel() {
        // nothing
    }

  private:
    void disableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (!mHbmFodEnabled) {
            return;
        }

        mMotoFingerprint->sendFodEvent(IMotFodEventType::FINGER_UP, {},
                                       [](IMotFodEventResult, const hidl_vec<signed char>&) {});
        setHbmState(OFF);

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        setHbmState(ON);
        mMotoFingerprint->sendFodEvent(IMotFodEventType::FINGER_DOWN, {},
                                       [](IMotFodEventResult, const hidl_vec<signed char>&) {});

        mHbmFodEnabled = true;
    }

    bool mHbmFodEnabled;
    std::mutex mSetHbmFodMutex;

    sp<IMotoFingerPrint> mMotoFingerprint;
};

static UdfpsHandler* create() {
    return new MotoUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
