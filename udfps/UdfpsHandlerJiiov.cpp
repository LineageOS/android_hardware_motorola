/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHandler.moto_jiiov"

#include <android-base/logging.h>

#include <dlfcn.h>

#include <chrono>
#include <mutex>
#include <thread>

#include "UdfpsHandlerMoto.h"

namespace {

constexpr const char* kAncLib = "anc.hal.so";

// anc.hal.so's generic command entry point. Forwards to the internal
// FpmExcuteCommand (vendor spelling) after unwrapping the device singleton.
using AncExcuteCommandFn = int (*)(void* device, int32_t cmd, const uint8_t* in, uint32_t in_len,
                                   uint8_t** out, uint32_t* out_len);
using GetFingerprintDeviceFn = void* (*)(void);

// FOD command IDs and payloads as used by the vendor stack. Verified against a
// known-working device implementation: cmd 13 signals finger down, cmd 14
// finger up, each carrying a fixed 3-byte payload.
constexpr int32_t ANC_CMD_FINGER_DOWN = 13;
constexpr int32_t ANC_CMD_FINGER_UP = 14;
constexpr uint8_t kFingerDownPayload[] = {3, 0, 99};
constexpr uint8_t kFingerUpPayload[] = {97, 98, 99};

// Delay between enabling HBM and notifying the sensor, giving the panel time to
// ramp brightness before the optical capture starts.
constexpr int kHbmSettleDelayMs = 30;

}  // namespace

class JiiovUdfpsHandler : public UdfpsHandler {
  public:
    void init(fingerprint_device_t* /*device*/) {
        mHbmFodEnabled = false;

        mAncHandle = dlopen(kAncLib, RTLD_NOW);
        if (!mAncHandle) {
            LOG(ERROR) << "Failed to dlopen " << kAncLib << ": " << dlerror();
            return;
        }

        auto getDevice =
                reinterpret_cast<GetFingerprintDeviceFn>(dlsym(mAncHandle, "GetFingerprintDevice"));
        mAncExcuteCommand =
                reinterpret_cast<AncExcuteCommandFn>(dlsym(mAncHandle, "AncExcuteCommand"));
        if (!getDevice || !mAncExcuteCommand) {
            LOG(ERROR) << kAncLib << " is missing required symbols "
                       << "(GetFingerprintDevice/AncExcuteCommand)";
            dlclose(mAncHandle);
            mAncHandle = nullptr;
            mAncExcuteCommand = nullptr;
            return;
        }

        // GetFingerprintDevice() returns a process-wide singleton, so it is
        // safe to cache here even though the fingerprint HAL owns its lifecycle.
        mAncDevice = getDevice();
        if (!mAncDevice) {
            LOG(ERROR) << "GetFingerprintDevice() returned null";
        }
    }

    ~JiiovUdfpsHandler() {
        if (mAncHandle) {
            dlclose(mAncHandle);
        }
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        if (SCREEN_WAKE_DELAY > 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(SCREEN_WAKE_DELAY));
        }
        enableHighBrightFod();
    }

    void onFingerUp() { disableHighBrightFod(); }

    void onAcquired(int32_t /*result*/, int32_t /*vendorCode*/) {
        // Intentionally a no-op: unlike the HIDL-service handlers, disabling HBM
        // on an acquired message would cut the panel brightness mid-capture and
        // break the optical read. The framework calls onFingerUp() when the
        // capture completes.
    }

    void cancel() { disableHighBrightFod(); }

  private:
    void disableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (!mHbmFodEnabled) {
            return;
        }

        sendFodCommand(ANC_CMD_FINGER_UP, kFingerUpPayload, sizeof(kFingerUpPayload));
        setHbmState(OFF);

        mHbmFodEnabled = false;
    }

    void enableHighBrightFod() {
        std::lock_guard<std::mutex> lock(mSetHbmFodMutex);

        if (mHbmFodEnabled) {
            return;
        }

        setHbmState(ON);
        std::this_thread::sleep_for(std::chrono::milliseconds(kHbmSettleDelayMs));
        sendFodCommand(ANC_CMD_FINGER_DOWN, kFingerDownPayload, sizeof(kFingerDownPayload));

        mHbmFodEnabled = true;
    }

    void sendFodCommand(int32_t cmd, const uint8_t* payload, uint32_t len) {
        if (!mAncExcuteCommand || !mAncDevice) {
            return;
        }

        uint8_t* out = nullptr;
        uint32_t out_len = 0;
        int ret = mAncExcuteCommand(mAncDevice, cmd, payload, len, &out, &out_len);
        if (ret != 0) {
            LOG(ERROR) << "AncExcuteCommand(" << cmd << ") failed: " << ret;
        }
    }

    bool mHbmFodEnabled;
    std::mutex mSetHbmFodMutex;

    void* mAncHandle = nullptr;
    void* mAncDevice = nullptr;
    AncExcuteCommandFn mAncExcuteCommand = nullptr;
};

static UdfpsHandler* create() {
    return new JiiovUdfpsHandler();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
        .create = create,
        .destroy = destroy,
};
