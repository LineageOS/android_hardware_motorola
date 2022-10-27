/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto"

#include <android-base/logging.h>
#include <android/hardware/biometrics/fingerprint/2.1/IBiometricsFingerprint.h>
#include <com/motorola/hardware/biometric/fingerprint/1.0/IMotoFingerPrint.h>
#include <com/motorola/hardware/display/panel/1.1/IDisplayPanel.h>

#include <fcntl.h>
#include <poll.h>
#include <fstream>

#include "UdfpsHandler.h"

using ::android::sp;
using ::android::hardware::hidl_vec;
using ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotFodEventResult;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotFodEventType;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotoFingerPrint;
using ::com::motorola::hardware::display::panel::V1_0::PanelColor;
using ::com::motorola::hardware::display::panel::V1_0::PanelMode;
using ::com::motorola::hardware::display::panel::V1_1::IDisplayPanel;

class MotoUdfpsHandler : public UdfpsHandler {
  public:
    void init(void* /*device*/) {
        mMotoFingerprint = IMotoFingerPrint::getService();
        mDisplayPanelService = IDisplayPanel::getService();
        mHbmFodEnabled = false;
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        enableHighBrightFod();
    }

    void onFingerUp() { disableHighBrightFod(); }

    void onAcquired(int32_t result, int32_t /*vendorCode*/) {
        if (result == static_cast<int32_t>(FingerprintAcquiredInfo::ACQUIRED_GOOD)) {
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

        // this is no mistake, setColor sets the PanelMode, while setMode sets the panel color
        mDisplayPanelService->setColor((PanelColor)PanelMode::PANEL_MODE_NORMAL);
        mMotoFingerprint->sendFodEvent(IMotFodEventType::FINGER_UP, {},
                                       [](IMotFodEventResult, const hidl_vec<signed char>&) {});

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        // this is no mistake, setColor sets the PanelMode, while setMode sets the panel color
        mDisplayPanelService->setColor((PanelColor)PanelMode::PANEL_MODE_HIGH_BRIGHT_FOD);
        mMotoFingerprint->sendFodEvent(IMotFodEventType::FINGER_DOWN, {},
                                       [](IMotFodEventResult, const hidl_vec<signed char>&) {});

        mHbmFodEnabled = true;
    }

    bool mHbmFodEnabled;
    std::mutex mSetHbmFodMutex;

    sp<IMotoFingerPrint> mMotoFingerprint;
    sp<IDisplayPanel> mDisplayPanelService;
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
