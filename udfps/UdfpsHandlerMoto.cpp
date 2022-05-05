/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto"

#include <android/hardware/biometrics/fingerprint/2.1/IBiometricsFingerprint.h>
#include <android-base/logging.h>
#include <com/motorola/hardware/biometric/fingerprint/1.0/IMotoFingerPrint.h>

#include <fcntl.h>
#include <poll.h>
#include <fstream>
#include <thread>

#include "UdfpsHandler.h"

// Touchscreen and HBM
#define FOD_UI_PATH "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/fod_ui"

using ::android::sp;
using ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo;
using ::android::hardware::hidl_vec;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotFodEventResult;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotFodEventType;
using ::com::motorola::hardware::biometric::fingerprint::V1_0::IMotoFingerPrint;

template <typename T>
static void set(const std::string& path, const T& value) {
    std::ofstream file(path);
    file << value;
}

static bool readBool(int fd) {
    char c;
    int rc;

    rc = lseek(fd, 0, SEEK_SET);
    if (rc) {
        LOG(ERROR) << "failed to seek fd, err: " << rc;
        return false;
    }

    rc = read(fd, &c, sizeof(char));
    if (rc != 1) {
        LOG(ERROR) << "failed to read bool from fd, err: " << rc;
        return false;
    }

    return c != '0';
}

class MotoUdfpsHandler : public UdfpsHandler {
  public:
    void init(void* /*device*/) {
        mMotoFingerprint = IMotoFingerPrint::getService();

        std::thread([this]() {
            int fd = open(FOD_UI_PATH, O_RDONLY);
            if (fd < 0) {
                LOG(ERROR) << "failed to open fd, err: " << fd;
                return;
            }

            struct pollfd fodUiPoll = {
                    .fd = fd,
                    .events = POLLERR | POLLPRI,
                    .revents = 0,
            };

            while (true) {
                int rc = poll(&fodUiPoll, 1, -1);
                if (rc < 0) {
                    LOG(ERROR) << "failed to poll fd, err: " << rc;
                    continue;
                }
                mMotoFingerprint->sendFodEvent(
                        readBool(fd) ? IMotFodEventType::FINGER_DOWN : IMotFodEventType::FINGER_UP,
                        {},
                        [](IMotFodEventResult /* result */,
                           const hidl_vec<signed char>& /* data */) {});
            }
        }).detach();
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        // nothing
    }

    void onFingerUp() {
        // nothing
    }

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
