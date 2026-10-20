/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/android/hardware/biometrics/face/BnFace.h>

namespace aidl::android::hardware::biometrics::face {

class Face : public BnFace {
  public:
    ndk::ScopedAStatus getSensorProps(std::vector<SensorProps>* out) override;
    ndk::ScopedAStatus createSession(int32_t sensorId, int32_t userId,
                                     const std::shared_ptr<ISessionCallback>& cb,
                                     std::shared_ptr<ISession>* out) override;
};

}  // namespace aidl::android::hardware::biometrics::face
