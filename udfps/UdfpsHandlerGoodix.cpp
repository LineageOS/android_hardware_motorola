/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto_goodix"

#include <vendor/goodix/hardware/biometrics/fingerprint/2.1/IGoodixFingerprintDaemon.h>

#include <chrono>
#include <thread>

#include "UdfpsHandlerMoto.h"

#define NOTIFY_FINGER_DOWN 1536
#define NOTIFY_FINGER_UP 1537

using ::android::sp;
using ::android::hardware::hidl_vec;
using ::vendor::goodix::hardware::biometrics::fingerprint::V2_1::IGoodixFingerprintDaemon;

class GoodixUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* /*device*/) {
        mGoodixFingerprint = IGoodixFingerprintDaemon::getService();
        mHbmFodEnabled = false;
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
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

        mGoodixFingerprint->sendCommand(
                NOTIFY_FINGER_UP, {},
                [](int /* result */, const hidl_vec<signed char>& /* data */) {});
        setHbmState(OFF);

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        setHbmState(ON);
        mGoodixFingerprint->sendCommand(
                NOTIFY_FINGER_DOWN, {},
                [](int /* result */, const hidl_vec<signed char>& /* data */) {});

        mHbmFodEnabled = true;
    }

    bool mHbmFodEnabled;
    std::mutex mSetHbmFodMutex;

    sp<IGoodixFingerprintDaemon> mGoodixFingerprint;
};

static UdfpsHandler* create() {
    return new GoodixUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
