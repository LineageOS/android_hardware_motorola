/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <aidl/android/hardware/keymaster/HardwareAuthToken.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <vector>

namespace com::motorola::hardware::biometric::face {

inline uint64_t toBigEndian64(uint64_t v) {
    uint64_t r = 0;
    for (int i = 0; i < 8; i++) r = (r << 8) | ((v >> (8 * i)) & 0xff);
    return r;
}
inline uint32_t toBigEndian32(uint32_t v) {
    return ((v & 0xff) << 24) | ((v & 0xff00) << 8) | ((v >> 8) & 0xff00) | (v >> 24);
}

/** Serialize to the 69-byte packed hw_auth_token_t the legacy HIDL face HAL used. */
inline std::vector<uint8_t> serializeHat(
        const aidl::android::hardware::keymaster::HardwareAuthToken& h) {
    std::vector<uint8_t> out(69, 0);
    uint8_t* p = out.data();
    p[0] = 0;  // version
    uint64_t challenge = static_cast<uint64_t>(h.challenge);
    uint64_t userId = static_cast<uint64_t>(h.userId);
    uint64_t authId = static_cast<uint64_t>(h.authenticatorId);
    uint32_t type = toBigEndian32(static_cast<uint32_t>(h.authenticatorType));
    uint64_t ts = toBigEndian64(static_cast<uint64_t>(h.timestamp.milliSeconds));
    memcpy(p + 1, &challenge, 8);
    memcpy(p + 9, &userId, 8);
    memcpy(p + 17, &authId, 8);
    memcpy(p + 25, &type, 4);
    memcpy(p + 29, &ts, 8);
    memcpy(p + 37, h.mac.data(), std::min<size_t>(32, h.mac.size()));
    return out;
}

}  // namespace com::motorola::hardware::biometric::face
