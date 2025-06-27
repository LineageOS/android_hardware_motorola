/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.touch-service.motorola"

#include "KeyDisabler.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/strings.h>

using ::android::base::ReadFileToString;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

namespace aidl {
namespace vendor {
namespace lineage {
namespace touch {


KeyDisabler::KeyDisabler() {
    mHasKeyDisabler = false;
    mFingerprintNavigation = IFingerprintNavigation::getService();
    if (mFingerprintNavigation != nullptr) {
        mHasKeyDisabler = true;
    }
}

ndk::ScopedAStatus KeyDisabler::getEnabled(bool* _aidl_return) {
    std::string buf;
    if (mHasKeyDisabler) {
        auto ret = mFingerprintNavigation->isEnabled();
        if (ret.isOk()) {
            *_aidl_return = ret;
            return ndk::ScopedAStatus::ok();
        } else {
            LOG(ERROR) << "Failed to call isEnabled on FingerprintNavigation";
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
        }
    }

    *_aidl_return = Trim(buf) == "0";
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus KeyDisabler::setEnabled(bool enabled) {
    if (mHasKeyDisabler) {
        auto status = mFingerprintNavigation->setNavigation(!enabled);
        if (!status.isOk()) {
            LOG(ERROR) << "Failed to call setNavigation on FingerprintNavigation";
            return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
        }
    }

    return ndk::ScopedAStatus::ok();
}

}  // namespace touch
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
