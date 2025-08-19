/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/vendor/lineage/livedisplay/BnAdaptiveBacklight.h>

namespace aidl {
namespace vendor {
namespace lineage {
namespace livedisplay {

class AdaptiveBacklight : public BnAdaptiveBacklight {
  public:
    AdaptiveBacklight();

    // Methods from ::aidl::vendor::lineage::livedisplay::BnAdaptiveBacklight follow.
    ndk::ScopedAStatus getEnabled(bool* _aidl_return) override;
    ndk::ScopedAStatus setEnabled(bool enabled) override;

  private:
    const char* file_;
};

}  // namespace livedisplay
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
