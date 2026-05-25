/*
 * SPDX-FileCopyrightText: 2019 The Android Open Source Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <android/hardware/sensors/2.1/types.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>

#include <log/log.h>
#include <condition_variable>
#include <filesystem>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

using ::android::hardware::sensors::V1_0::OperationMode;
using ::android::hardware::sensors::V1_0::Result;
using ::android::hardware::sensors::V2_1::Event;
using ::android::hardware::sensors::V2_1::SensorInfo;
using ::android::hardware::sensors::V2_1::SensorType;

namespace android {
namespace hardware {
namespace sensors {
namespace V2_1 {
namespace subhal {
namespace implementation {

class ISensorsEventCallback {
  public:
    virtual ~ISensorsEventCallback() {};
    virtual void postEvents(const std::vector<Event>& events, bool wakeup) = 0;
};

class Sensor {
  public:
    Sensor(int32_t sensorHandle, ISensorsEventCallback* callback);
    virtual bool opened() { return true; }
    virtual ~Sensor();

    const SensorInfo& getSensorInfo() const;
    virtual void batch(int32_t samplingPeriodNs);
    virtual void activate(bool enable);
    virtual Result flush();

    virtual void setOperationMode(OperationMode mode);
    bool supportsDataInjection() const;
    Result injectEvent(const Event& event);

  protected:
    virtual void run();
    virtual std::vector<Event> readEvents();
    static void startThread(Sensor* sensor);

    bool isWakeUpSensor();

    bool mIsEnabled;
    int64_t mSamplingPeriodNs;
    int64_t mLastSampleTimeNs;
    SensorInfo mSensorInfo;

    std::atomic_bool mStopThread;
    std::condition_variable mWaitCV;
    std::mutex mRunMutex;
    std::thread mRunThread;

    ISensorsEventCallback* mCallback;

    OperationMode mMode;
};

class OneShotSensor : public Sensor {
  public:
    OneShotSensor(int32_t sensorHandle, ISensorsEventCallback* callback);

    virtual void batch(int32_t /* samplingPeriodNs */) override {}

    virtual Result flush() override { return Result::BAD_VALUE; }
};

class SysfsPollingOneShotSensor : public OneShotSensor {
  public:
    SysfsPollingOneShotSensor(int32_t sensorHandle, ISensorsEventCallback* callback,
                              const std::string& pollPath, const std::string& enablePath,
                              const std::string& name, const std::string& typeAsString,
                              SensorType type);
    virtual bool opened();
    virtual ~SysfsPollingOneShotSensor() override;

    virtual void activate(bool enable) override;
    virtual void activate(bool enable, bool notify, bool lock);
    virtual void writeEnable(bool enable);
    virtual void setOperationMode(OperationMode mode) override;
    virtual std::vector<Event> readEvents() override;
    virtual void fillEventData(Event& event);
    virtual bool readFd(const int fd);

  protected:
    virtual void run() override;

    std::ofstream mEnableStream;

  private:
    void interruptPoll();

    struct pollfd mPolls[2];
    int mWaitPipeFd[2];
    int mPollFd;
};

class TouchscreenSysfsSensor : public SysfsPollingOneShotSensor {
  public:
    TouchscreenSysfsSensor(int32_t sensorHandle, ISensorsEventCallback* callback,
                      const std::string& pollFile, const std::string& enableFile,
                      const std::string& name, const std::string& typeAsString, SensorType type)
        : SysfsPollingOneShotSensor(sensorHandle, callback, getTouchscreenBasePath() + pollFile,
                                    getTouchscreenBasePath() + enableFile, name, typeAsString,
                                    type) {}

  private:
    static std::string getTouchscreenBasePath() {
        static std::string basePath;
        if (!basePath.empty()) return basePath;

        const std::filesystem::path socBase("/sys/devices/platform/soc");
        static constexpr std::string_view socs[] = {"4a80000.spi", "998000.spi", "a8c000.spi",
                                                    "a94000.spi", "a94000.i2c"};
        static constexpr std::string_view spis[] = {"spi0.0", "spi0.1"};
        static constexpr std::string_view displays[] = {"NVT-ts", "primary", "secondary"};

        for (const auto& soc : socs) {
            std::filesystem::path path = socBase / soc;
            if (soc.find(".i2c") != std::string_view::npos) {
                path /= "i2c-2/2-0049";
            } else {
                bool found = false;
                for (const auto& spi : spis) {
                    std::filesystem::path spiPath = path / "spi_master/spi0" / spi;
                    if (std::filesystem::exists(spiPath)) {
                        path = spiPath;
                        found = true;
                        break;
                    }
                }
                if (!found) continue;
            }

            for (const auto& display : displays) {
                std::filesystem::path displayPath = path / "touchscreen" / display;
                if (std::filesystem::exists(displayPath)) {
                    basePath = displayPath.string() + "/";
                    ALOGI("Detected touchscreen base path: %s", basePath.c_str());
                    return basePath;
                }
            }
        }

        basePath = "/sys/class/touchscreen/primary/";
        ALOGW("No touchscreen base path detected, falling back to: %s", basePath.c_str());
        return basePath;
    }
};

constexpr int32_t SENSOR_TYPE_BASE = static_cast<int32_t>(SensorType::DEVICE_PRIVATE_BASE) + 100;

#ifdef ENABLE_DOUBLE_TAP
class DoubleTapSensor : public TouchscreenSysfsSensor {
  public:
    DoubleTapSensor(int32_t sensorHandle, ISensorsEventCallback* callback)
        : TouchscreenSysfsSensor(sensorHandle, callback, "double_tap_pressed", "double_tap_enabled",
                            "Double Tap Sensor", "org.lineageos.sensor.double_tap",
                            static_cast<SensorType>(SENSOR_TYPE_BASE + 1)) {}
};
#endif

#ifdef ENABLE_UDFPS
class UdfpsSensor : public TouchscreenSysfsSensor {
  public:
    UdfpsSensor(int32_t sensorHandle, ISensorsEventCallback* callback)
        : TouchscreenSysfsSensor(sensorHandle, callback, "udfps_pressed", "udfps_enabled",
                            "UDFPS Sensor", "org.lineageos.sensor.udfps",
                            static_cast<SensorType>(SENSOR_TYPE_BASE + 2)) {}
};
#endif

}  // namespace implementation
}  // namespace subhal
}  // namespace V2_1
}  // namespace sensors
}  // namespace hardware
}  // namespace android
