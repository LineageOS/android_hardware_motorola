/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/touch/BnKeyDisabler.h>
#include <com/fingerprints/extension/1.0/IFingerprintNavigation.h>

using ::com::fingerprints::extension::V1_0::IFingerprintNavigation;

namespace aidl {
namespace vendor {
namespace lineage {
namespace touch {

class KeyDisabler : public BnKeyDisabler {
  public:
    ndk::ScopedAStatus getEnabled(bool* _aidl_return) override;
    ndk::ScopedAStatus setEnabled(bool enabled) override;

    KeyDisabler();

  private:
    bool mHasKeyDisabler;
    ::android::sp<IFingerprintNavigation> mFingerprintNavigation;
};

}  // namespace touch
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
