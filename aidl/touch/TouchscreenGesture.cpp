/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.touch-service.motorola"

#include "TouchscreenGesture.h"

#include <fstream>

#include <android-base/file.h>
#include <android-base/logging.h>

namespace aidl {
namespace vendor {
namespace lineage {
namespace touch {

ndk::ScopedAStatus TouchscreenGesture::getSupportedGestures(std::vector<Gesture>* _aidl_return) {
    std::vector<Gesture> gestures;
    for (int32_t i = 0; i < std::size(kGestureNodes); ++i) {
        gestures.push_back({i, kGestureNodes[i].name, kGestureNodes[i].keycode});
    }

    *_aidl_return = gestures;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus TouchscreenGesture::setGestureEnabled(const Gesture& gesture, bool enable) {
    if (gesture.id >= std::size(kGestureNodes)) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    if (!android::base::WriteStringToFile(enable ? kGestureNodes[gesture.id].enable : kGestureNodes[gesture.id].disable,
                                          kGestureNodes[gesture.id].path)) {
        LOG(ERROR) << "Writing to file " << kGestureNodes[gesture.id].path << " failed";
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    return ndk::ScopedAStatus::ok();
}

}  // namespace touch
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
