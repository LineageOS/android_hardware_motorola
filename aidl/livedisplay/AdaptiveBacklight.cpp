/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "AdaptiveBacklightService"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <livedisplay/motorola/AdaptiveBacklight.h>

using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace aidl {
namespace vendor {
namespace lineage {
namespace livedisplay {

static constexpr const char* kAdaptiveBacklightPaths[] = {
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/cabc",
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dsi_display_acl",
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/dsi_display_cabc",
};

AdaptiveBacklight::AdaptiveBacklight() {
    file_ = nullptr;
    for (const auto& path : kAdaptiveBacklightPaths) {
        if (!access(path, R_OK | W_OK)) {
            file_ = path;
            break;
        }
    }
}

ndk::ScopedAStatus AdaptiveBacklight::getEnabled(bool* _aidl_return) {
    if (file_ == nullptr) {
        *_aidl_return = false;
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    std::string tmp;
    int32_t contents = 0;

    if (ReadFileToString(file_, &tmp)) {
        contents = std::stoi(Trim(tmp));
    }

    *_aidl_return = contents > 0;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus AdaptiveBacklight::setEnabled(bool enabled) {
    if (file_ == nullptr) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    if (!WriteStringToFile(enabled ? "1" : "0", file_, true)) {
        LOG(ERROR) << "Failed to set AdaptiveBacklight state";
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    return ndk::ScopedAStatus::ok();
}

}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
