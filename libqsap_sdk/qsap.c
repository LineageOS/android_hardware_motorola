/*
 * SPDX-FileCopyrightText: 2010-2013 The Linux Foundation
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: BSD-3-Clause
 */

#include <string.h>

#include <linux/wireless.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#include "qsap_api.h"

extern struct Command qsap_str[];

s32 is_softap_enabled(void) {
    s32 mode = 0;
    int ret;

    ret = qsap_get_mode(&mode);
    if (eSUCCESS != ret) {
        ALOGD("Failed to get the mode of operation\n");
        return eERR_UNKNOWN;
    }

    if (mode == IW_MODE_MASTER) {
        ALOGD("HOSTAPD Enabled\n");
        return ENABLE;
    }

    ALOGD("HOSTAPD Disabled\n");

    return DISABLE;
}

s32 wifi_qsap_set_tx_power(s32 tx_power) {
#define QCSAP_IOCTL_SET_MAX_TX_POWER (SIOCIWFIRSTPRIV + 22)
    s32 sock;
    s32 ret = eERR_SET_TX_POWER;
    s8 interface[128];
    s8* iface;
    s32 len = 128;
    struct iwreq wrq;

    if (NULL == (iface = qsap_get_config_value(CONFIG_FILE, &qsap_str[STR_INTERFACE], interface,
                                               (u32*)&len))) {
        ALOGE("%s :interface error \n", __func__);
        return ret;
    }

    /* Issue QCSAP_IOCTL_SET_MAX_TX_POWER ioctl */
    sock = socket(AF_INET, SOCK_DGRAM, 0);

    if (sock < 0) {
        ALOGE("%s :socket error \n", __func__);
        return eERR_SET_TX_POWER;
    }

    strlcpy(wrq.ifr_name, iface, sizeof(wrq.ifr_name));
    wrq.u.data.length = sizeof(s32);
    wrq.u.data.pointer = &tx_power;
    wrq.u.data.flags = 0;

    ret = ioctl(sock, QCSAP_IOCTL_SET_MAX_TX_POWER, &wrq);
    close(sock);

    if (ret) {
        ALOGE("%s :IOCTL set tx power failed: %d\n", __func__, ret);
        ret = eERR_SET_TX_POWER;
    } else {
        ALOGD("%s :IOCTL set tx power issued\n", __func__);
        ret = eSUCCESS;
    }

    return ret;
}
