/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/touch/BnTouchscreenGesture.h>

#include <map>

namespace {
struct GestureInfo {
    int32_t keycode;
    const char* name;
    const char* path;
    const char* enable;
    const char* disable;
};

constexpr GestureInfo kGestureNodes[] = {
        {0x2c2, "Single Tap", SINGLE_TAP_CONTROL_PATH, "1", "0"},
};
}  // anonymous namespace

namespace aidl {
namespace vendor {
namespace lineage {
namespace touch {

class TouchscreenGesture : public BnTouchscreenGesture {
  public:
    ndk::ScopedAStatus getSupportedGestures(std::vector<Gesture>* _aidl_return) override;
    ndk::ScopedAStatus setGestureEnabled(const Gesture& gesture, bool enabled) override;

   private:
    std::map<int32_t, GestureInfo> kGestureInfoMap;
};

}  // namespace touch
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
