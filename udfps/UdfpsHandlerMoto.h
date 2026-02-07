/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android-base/logging.h>

#include <fcntl.h>
#include <fstream>

#include "UdfpsHandler.h"

#include <display/drm/sde_drm.h>

enum HBM_STATE { OFF = 0, ON = 2 };

inline void setHbmState(int state) {
    struct panel_param_info param_info;
    int32_t node = open("/dev/dri/card0", O_RDWR);
    int32_t ret = 0;

    if (node < 0) {
        LOG(ERROR) << "Failed to get card0!";
        return;
    }

    param_info.param_idx = PARAM_HBM;
    param_info.value = state;

    ret = ioctl(node, DRM_IOCTL_SET_PANEL_FEATURE, &param_info);
    if (ret < 0) {
        LOG(ERROR) << "IOCTL call failed with ret = " << ret;
    } else {
        LOG(INFO) << "HBM state set successfully. New state: " << state;
    }

    close(node);
}
