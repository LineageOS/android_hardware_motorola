/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <com/motorola/hardware/biometric/fingerprint/1.0/IMotoFingerPrint.h>

#include <fcntl.h>
#include <poll.h>
#include <chrono>
#include <fstream>
#include <thread>

#include "UdfpsHandler.h"

#define FOD_HBM_PATH "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/fod_hbm"

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
        enableHighBrightFod();
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
            onFingerUp();
        }).detach();
    }

    void onFingerUp() { disableHighBrightFod(); }

    void onAcquired(int32_t /*result*/, int32_t /*vendorCode*/) {
        // nothing
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
        android::base::WriteStringToFile("0", FOD_HBM_PATH);

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        android::base::WriteStringToFile("1", FOD_HBM_PATH);
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
