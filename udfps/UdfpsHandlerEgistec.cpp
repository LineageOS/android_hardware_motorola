/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto_egistec"

#include <vendor/egistec/hardware/fingerprint/4.0/IBiometricsFingerprintRbs.h>

#include <chrono>
#include <thread>

#include "UdfpsHandlerMoto.h"

using ::android::sp;
using ::android::hardware::hidl_vec;
using ::vendor::egistec::hardware::fingerprint::V4_0::IBiometricsFingerprintRbs;

class EgistecUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* /*device*/) {
        mRbsFingerprint = IBiometricsFingerprintRbs::getService();
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

        extraApiWrapper(102);
        setHbmState(OFF);

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        setHbmState(ON);
        extraApiWrapper(101);

        mHbmFodEnabled = true;
    }

    void extraApiWrapper(int cidValue) {
        int cid[1] = {cidValue};
        // Create a std::vector<uint8_t> to store the data from 'cid'
        std::vector<uint8_t> cid_data(reinterpret_cast<uint8_t*>(cid),
                                      reinterpret_cast<uint8_t*>(cid) + sizeof(cid));
        // Create the hidl_vec<uint8_t> from the std::vector<uint8_t>
        ::android::hardware::hidl_vec<uint8_t> hidl_cid = cid_data;
        // Call extra_api with the correct input buffer and an empty lambda callback
        mRbsFingerprint->extra_api(7, hidl_cid,
                                   [](const ::android::hardware::hidl_vec<uint8_t>&) {});
    }

    bool mHbmFodEnabled;
    std::mutex mSetHbmFodMutex;

    sp<IBiometricsFingerprintRbs> mRbsFingerprint;
};

static UdfpsHandler* create() {
    return new EgistecUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
