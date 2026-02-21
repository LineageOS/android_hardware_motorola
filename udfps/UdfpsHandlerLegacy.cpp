/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto_legacy"

#include <vendor/goodix/hardware/biometrics/fingerprint/2.1/IGoodixFingerprintDaemon.h>

#include <poll.h>
#include <thread>

#include "UdfpsHandlerMoto.h"

#define NOTIFY_FINGER_DOWN 1536
#define NOTIFY_FINGER_UP 1537

#define FOD_UI_PATH "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/fod_ui"

using ::android::sp;
using ::android::hardware::hidl_vec;
using ::vendor::goodix::hardware::biometrics::fingerprint::V2_1::IGoodixFingerprintDaemon;

class LegacyUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* /*device*/) {
        mGoodixFingerprint = IGoodixFingerprintDaemon::getService();

        std::thread([this]() {
            int fd = open(FOD_UI_PATH, O_RDONLY);
            if (fd < 0) {
                LOG(ERROR) << "failed to open " << FOD_UI_PATH << " , err: " << fd;
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
                    LOG(ERROR) << "failed to poll " << FOD_UI_PATH << ", err: " << rc;
                    continue;
                }

                mGoodixFingerprint->sendCommand(
                        readBool(fd) ? NOTIFY_FINGER_DOWN : NOTIFY_FINGER_UP, {},
                        [](int /* result */, const hidl_vec<signed char>& /* data */) {});
            }
        }).detach();
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        // nothing
    }

    void onFingerUp() {
        // nothing
    }

    void onAcquired(int32_t /*result*/, int32_t /*vendorCode*/) {
        // nothing
    }

    void cancel() {
        // nothing
    }

  private:
    sp<IGoodixFingerprintDaemon> mGoodixFingerprint;
};

static UdfpsHandler* create() {
    return new LegacyUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
