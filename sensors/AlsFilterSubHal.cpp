/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "sensors.moto_als"

#include <V2_0/SubHal.h>
#include <android/hardware/sensors/1.0/types.h>
#include <log/log.h>

#include <dlfcn.h>
#include <fcntl.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <cstdlib>
#include <mutex>
#include <string>
#include <vector>

using ::android::sp;
using ::android::hardware::hidl_handle;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::sensors::V1_0::Event;
using ::android::hardware::sensors::V1_0::OperationMode;
using ::android::hardware::sensors::V1_0::RateLevel;
using ::android::hardware::sensors::V1_0::Result;
using ::android::hardware::sensors::V1_0::SensorFlagBits;
using ::android::hardware::sensors::V1_0::SensorInfo;
using ::android::hardware::sensors::V1_0::SensorType;
using ::android::hardware::sensors::V1_0::SharedMemInfo;
using ::android::hardware::sensors::V2_0::implementation::IHalProxyCallback;
using ::android::hardware::sensors::V2_0::implementation::ISensorsSubHal;
using ::android::hardware::sensors::V2_0::implementation::ScopedWakelock;

namespace {

// LED node(s) to watch. On xpeng the notification LED is exposed as "charging".
const char* const kLedPaths[] = {"/sys/class/leds/charging/brightness"};

// Keep holding the last clean lux this long after the LED was last seen lit.
// Must exceed the LED blink "off" period plus the sensor integration lag.
constexpr int64_t kHoldoffNs = 2500LL * 1000 * 1000;

int64_t nowNs() {
    timespec ts;
    clock_gettime(CLOCK_BOOTTIME, &ts);
    return int64_t(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

bool ledLit() {
    for (const char* path : kLedPaths) {
        int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        char buf[16] = {0};
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n > 0 && atoi(buf) > 0) return true;
    }
    return false;
}

class AlsGate {
  public:
    void setHandle(int32_t handle) { mHandle.store(handle); }
    int32_t handle() const { return mHandle.load(); }

    // Returns false if the event must be dropped. May rewrite the lux value.
    bool filter(Event& e) {
        std::lock_guard<std::mutex> lock(mLock);
        const int64_t now = nowNs();
        if (ledLit()) mLastLitNs = now;
        const bool active = mLastLitNs != 0 && (now - mLastLitNs) < kHoldoffNs;
        if (active) {
            if (!mHaveClean) return false;  // LED lit before any clean sample
            e.u.scalar = mLastClean;
            return true;
        }
        mLastClean = e.u.scalar;
        mHaveClean = true;
        return true;
    }

  private:
    std::atomic<int32_t> mHandle{-1};
    std::mutex mLock;
    int64_t mLastLitNs = 0;
    float mLastClean = 0.0f;
    bool mHaveClean = false;
};

class FilteringCallback : public IHalProxyCallback {
  public:
    FilteringCallback(const sp<IHalProxyCallback>& real, AlsGate* gate)
        : mReal(real), mGate(gate) {}

    Return<void> onDynamicSensorsConnected(const hidl_vec<SensorInfo>& l) override {
        return mReal->onDynamicSensorsConnected(l);
    }
    Return<void> onDynamicSensorsDisconnected(const hidl_vec<int32_t>& h) override {
        return mReal->onDynamicSensorsDisconnected(h);
    }

    void postEvents(const std::vector<Event>& events, ScopedWakelock wakelock) override {
        std::vector<Event> out;
        out.reserve(events.size());
        for (Event e : events) {
            if (e.sensorType == SensorType::LIGHT && e.sensorHandle == mGate->handle()) {
                if (!mGate->filter(e)) continue;
            }
            out.push_back(e);
        }
        // If everything was dropped, 'wakelock' goes out of scope and releases.
        if (!out.empty()) mReal->postEvents(out, std::move(wakelock));
    }

    ScopedWakelock createScopedWakelock(bool lock) override {
        return mReal->createScopedWakelock(lock);
    }

  private:
    sp<IHalProxyCallback> mReal;
    AlsGate* mGate;
};

class XpengAlsSubHal : public ISensorsSubHal {
  public:
    bool load() {
        void* lib = dlopen("sensors.ssc.so", RTLD_NOW | RTLD_LOCAL);
        if (!lib) {
            ALOGE("dlopen sensors.ssc.so failed: %s", dlerror());
            return false;
        }
        using GetFn = ISensorsSubHal* (*)(uint32_t*);
        auto fn = reinterpret_cast<GetFn>(dlsym(lib, "sensorsHalGetSubHal"));
        if (!fn) {
            ALOGE("sensorsHalGetSubHal not found in sensors.ssc.so");
            return false;
        }
        uint32_t version = 0;
        mInner = fn(&version);
        return mInner != nullptr;
    }

    const std::string getName() override { return "xpeng-als-filter"; }

    Return<Result> initialize(const sp<IHalProxyCallback>& cb) override {
        mCallback = new FilteringCallback(cb, &mGate);
        return mInner->initialize(mCallback);
    }

    Return<void> getSensorsList(getSensorsList_cb hidl_cb) override {
        return mInner->getSensorsList([&](const hidl_vec<SensorInfo>& list) {
            for (const auto& s : list) {
                const bool wakeup = s.flags & static_cast<uint32_t>(SensorFlagBits::WAKE_UP);
                if (s.type == SensorType::LIGHT && !wakeup) mGate.setHandle(s.sensorHandle);
            }
            hidl_cb(list);
        });
    }

    Return<Result> setOperationMode(OperationMode m) override { return mInner->setOperationMode(m); }
    Return<Result> activate(int32_t h, bool en) override { return mInner->activate(h, en); }
    Return<Result> batch(int32_t h, int64_t p, int64_t l) override { return mInner->batch(h, p, l); }
    Return<Result> flush(int32_t h) override { return mInner->flush(h); }
    Return<Result> injectSensorData(const Event& e) override { return mInner->injectSensorData(e); }
    Return<void> registerDirectChannel(const SharedMemInfo& m, registerDirectChannel_cb cb) override {
        return mInner->registerDirectChannel(m, cb);
    }
    Return<Result> unregisterDirectChannel(int32_t c) override {
        return mInner->unregisterDirectChannel(c);
    }
    Return<void> configDirectReport(int32_t s, int32_t c, RateLevel r, configDirectReport_cb cb) override {
        return mInner->configDirectReport(s, c, r, cb);
    }
    Return<void> debug(const hidl_handle& fd, const hidl_vec<hidl_string>& args) override {
        return mInner->debug(fd, args);
    }

  private:
    ISensorsSubHal* mInner = nullptr;
    AlsGate mGate;
    sp<IHalProxyCallback> mCallback;
};

}  // namespace

extern "C" ISensorsSubHal* sensorsHalGetSubHal(uint32_t* version) {
    static XpengAlsSubHal* hal = [] {
        auto* h = new XpengAlsSubHal();
        if (!h->load()) {
            delete h;
            return static_cast<XpengAlsSubHal*>(nullptr);
        }
        return h;
    }();
    *version = SUB_HAL_2_0_VERSION;
    return hal;
}
