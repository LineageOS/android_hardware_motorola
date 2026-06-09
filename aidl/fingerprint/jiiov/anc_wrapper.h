// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <hardware/hardware.h>

namespace jiiov {

// Attempts to load and initialize anc.hal.so (Jiiov's non-standard
// vendor HAL) and wrap it behind a conforming fingerprint_device_t,
// so the rest of the AIDL HAL can treat it like any other legacy
// vendor sensor. Returns nullptr on any failure.
fingerprint_device_t* OpenAncHal();

}  // namespace jiiov
