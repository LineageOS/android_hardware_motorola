/*
 * SPDX-FileCopyrightText: 2010-2013 The Linux Foundation
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: BSD-3-Clause
 */

#include <errno.h>
#include <linux/wireless.h>
#include <net/if.h>
#include <netlink/genl/ctrl.h>
#include <netlink/genl/genl.h>
#include <netlink/msg.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include "nl80211_copy.h"

#include "qsap_api.h"

#define QCSAP_IOCTL_GET_CHANNEL (SIOCIWFIRSTPRIV + 9)

/*
 * WARNING: On updating the cmd_list, the enum esap_cmd in file
 * qsap_api.h must be updates to reflect the changes
 */
static struct Command cmd_list[eCMD_LAST] = {
        {"ssid", "QualcommSoftAP"},
        {"ignore_broadcast_ssid", "0"},
        {"channel", "1"},
        {"beacon_int", "100"},
        {"dtim_period", "2"},
        {"hw_mode", "n"},
        {"auth_algs", "3"},
        {"security_mode", "0"},
        {"wep_key0", NULL},
        {"wep_key1", NULL},
        {"wep_key2", NULL},
        {"wep_key3", NULL},
        {"wep_default_key", NULL},
        {"wpa_passphrase", NULL},
        {"wpa_pairwise", NULL},
        {"rsn_pairwise", NULL},
        {"mac_address", "00deadbeef04"},
        {"reset_ap", NULL},
        {"macaddr_acl", "0"},
        {"add_to_allow_list", NULL},
        {"add_to_deny_list", NULL},
        {"remove_from_allow_list", NULL},
        {"remove_from_deny_list", NULL},
        {"allow_list", ""},
        {"deny_list", ""},
        {"commit", NULL},
        {"enable_softap", NULL},
        {"disassoc_sta", NULL},
        {"reset_to_default", NULL},
        {"protection_flag", "1"},
        {"data_rate", "0"},
        {"sta_mac_list", NULL},
        {"tx_power", "27"},
        {"sdk_version", "0.0.1.0"},
        {"wmm_enabled", "0"},

        /** Warning: Do not change the order of the WPS commands */
        {"wps_state", "0"},
        {"config_methods", NULL},
        {"uuid", NULL},
        {"device_name", NULL},
        {"manufacturer", NULL},
        {"model_name", NULL},
        {"model_number", NULL},
        {"serial_number", NULL},
        {"device_type", NULL},
        {"os_version", NULL},
        {"friendly_name", NULL},
        {"manufacturer_url", NULL},
        {"model_description", NULL},
        {"model_url", NULL},
        {"upc", NULL},
        /************ WPS commands end *********/

        {"fragm_threshold", NULL},
        {"rts_threshold", NULL},
        {"wpa_group_rekey", NULL},
        {"country_code", NULL},
        {"ap_isolate", NULL},
        {"ieee80211d", NULL},
        {"apstat", NULL},
        {"auto_shut_off_time", NULL},
        {"energy_detect_threshold", "128"},
        {"basic_rates", NULL},
        {"require_ht", NULL},
        {"ieee80211n", "1"},
        {"setchannelrange", NULL},
        {"autochannel", NULL},
        {"ieee80211w", NULL},
        {"wpa_key_mgmt", NULL},
        {"max_num_sta", "8"},
        {"ieee80211ac", NULL},
        {"vht_oper_chwidth", NULL},
        {"chanlist", NULL},
        {"ht_capab", NULL},
        {"ieee80211h", NULL},
        {"enable_wigig_softap", NULL},
        {"interface", NULL},
        {"ssid2", NULL},
        {"bridge", NULL},
        {"ctrl_interface", NULL},
        {"vendor_elements", NULL},
        {"assocresp_elements", NULL},
        {"acs_exclude_dfs", NULL},
        {"wowlan_triggers", "any"},
        {"accept_mac_file", NULL},
        {"deny_mac_file", NULL},
        {"owe_transition_ifname", NULL},
        {"sae_require_mfp", NULL},
        {"ieee80211ax", NULL},
        {"enable_edmg", NULL},
        {"edmg_channel", NULL},

};

struct Command qsap_str[eSTR_LAST] = {
        {"wpa", NULL},
        {"accept_mac_file", NULL},
        {"deny_mac_file", NULL},
        {"gAPMacAddr", "00deadbeef04"},      /** AP MAC address */
        {"gEnableApProt", "1"},              /** protection flag in ini file */
        {"gFixedRate", "0"},                 /** Fixed rate in ini */
        {"gTxPowerCap", "27"},               /** Tx power in ini */
        {"gFragmentationThreshold", "2346"}, /** Fragmentation threshold in ini */
        {"RTSThreshold", "2347"},            /** RTS threshold in ini */
        {"gAPCntryCode", "USI"},             /** Country code in ini */
        {"gDisableIntraBssFwd", "0"},        /** Intra-bss forward in ini */
        {"WmmIsEnabled", "0"},               /** WMM */
        {"g11dSupportEnabled", "1"},         /** 802.11d support */
        {"ieee80211n", NULL},
        {"ctrl_interface", NULL},
        {"interface", NULL},
        {"eap_server", NULL},
        {"gAPAutoShutOff", "0"},
        {"gEnablePhyAgcListenMode", "128"},
};

/** configuration file path */
char* pconffile = CONFIG_FILE;

/**
 *    Get the channel being used in the soft AP.
 */
int qsap_get_operating_channel(s32* pchan) {
    int sock;
    struct iwreq wrq;
    s8 interface[MAX_CONF_LINE_LEN];
    u32 len = MAX_CONF_LINE_LEN;
    s8* pif;
    int ret;

    if (ENABLE != is_softap_enabled()) {
        goto error;
    }

    if (NULL ==
        (pif = qsap_get_config_value(pconffile, &qsap_str[STR_INTERFACE], interface, &len))) {
        ALOGE("%s :interface error \n", __func__);
        goto error;
    }

    interface[len] = '\0';

    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        ALOGE("%s :socket error \n", __func__);
        goto error;
    }

    *pchan = 0;

    strlcpy(wrq.ifr_name, pif, sizeof(wrq.ifr_name));
    wrq.u.data.length = sizeof(s32);
    wrq.u.data.pointer = pchan;
    wrq.u.data.flags = 0;

    ret = ioctl(sock, QCSAP_IOCTL_GET_CHANNEL, &wrq);
    if (ret < 0) {
        ALOGE("%s: ioctl failure \n", __func__);
        close(sock);
        goto error;
    }

    ALOGE("Recv len :%d \n", wrq.u.data.length);
    *pchan = *(int*)(&wrq.u.name[0]);
    ALOGE("Operating channel :%d \n", *pchan);
    close(sock);
    return eSUCCESS;

error:
    *pchan = 0;
    ALOGE("%s: Failed to read channel \n", __func__);
    return eERR_CHAN_READ;
}

static int iftypeCallback(struct nl_msg* msg, void* arg) {
    struct nlmsghdr* ret_hdr = nlmsg_hdr(msg);
    struct nlattr* tb_msg[NL80211_ATTR_MAX + 1];
    int* type = arg;

    struct genlmsghdr* gnlh = (struct genlmsghdr*)nlmsg_data(ret_hdr);

    nla_parse(tb_msg, NL80211_ATTR_MAX, genlmsg_attrdata(gnlh, 0), genlmsg_attrlen(gnlh, 0), NULL);

    if (tb_msg[NL80211_ATTR_IFTYPE]) {
        *type = nla_get_u32(tb_msg[NL80211_ATTR_IFTYPE]);
    }
    return NL_SKIP;
}

/**
 *    Get the mode of operation.
 */
int qsap_get_mode(s32* pmode) {
    int ret = eERR_UNKNOWN;
    struct nl_sock* sk = NULL;
    int nl80211_id = -1;
    int if_type = -1;
    s8 interface[MAX_CONF_LINE_LEN];
    u32 len = MAX_CONF_LINE_LEN;
    s8* pif;
    struct nl_msg* msg = NULL;

    // get interface name
    if (NULL ==
        (pif = qsap_get_config_value(pconffile, &qsap_str[STR_INTERFACE], interface, &len))) {
        ALOGD("%s :interface error \n", __func__);
        goto nla_put_failure;
    }

    interface[len] = '\0';

    // allocate socket
    sk = nl_socket_alloc();

    // return if socket allocation fails
    if (sk == NULL) {
        ALOGE("socket allocation failure");
        return ret;
    }

    // connect to generic netlink
    if (genl_connect(sk)) {
        ALOGE("Netlink socket Connection failure");
        ret = eERR_UNKNOWN;
        goto nla_put_failure;
    }

    // find the nl80211 driver ID
    nl80211_id = genl_ctrl_resolve(sk, "nl80211");

    // attach a callback
    nl_socket_modify_cb(sk, NL_CB_VALID, NL_CB_CUSTOM, iftypeCallback, &if_type);

    // allocate a message
    msg = nlmsg_alloc();

    // return if message allocation fails
    if (msg == NULL) {
        ALOGE("message allocation failure");
        goto nla_put_failure;
    }

    // setup the message
    genlmsg_put(msg, 0, 0, nl80211_id, 0, 0, NL80211_CMD_GET_INTERFACE, 0);

    // add message attributes
    NLA_PUT_U32(msg, NL80211_ATTR_IFINDEX, if_nametoindex(pif));

    // Send the message
    ret = nl_send_auto_complete(sk, msg);
    if (ret < 0) {
        ALOGE("nl_send_auto_complete failure");
        ret = eERR_UNKNOWN;
        goto nla_put_failure;
    }

    // block for message to return
    nl_recvmsgs_default(sk);
    *pmode = if_type;
    ALOGI("%s: (%s) NL80211 Get Mode = %d \n", __func__, pif, (int)*pmode);
    ret = eSUCCESS;

nla_put_failure:
    if (sk) nl_socket_free(sk);
    if (msg) nlmsg_free(msg);
    return ret;
}

static int qsap_scnprintf(char* str, size_t size, const char* format, ...) {
    va_list arg_ptr;
    int ret = 0;

    if (size < 1) return 0;
    va_start(arg_ptr, format);
    ret = vsnprintf(str, size, format, arg_ptr);
    va_end(arg_ptr);
    return (ret < (int)size) ? ret : (int)(size - 1);
}

/**
 * @brief
 *        For a give configuration parameter, read the configuration value from the file.
 * @param pfile [IN] configuration file path
 * @param pcmd [IN] pointer to the comand structure
 * @param presp [OUT] buffer to store the configuration value
 * @param plen [IN-OUT] The length of the buffer is provided as input.
 *                      The length of the configuration parameter value, stored
 *                      in the 'presp', is provided as the output
 * @param ignore_comment [IN] if set, read the commented value also
 * @return void
 */
static s32 qsap_read_cfg(s8* pfile, struct Command* pcmd, s8* presp, u32* plen, s8* var,
                         s32 ignore_comment) {
    FILE* fcfg;
    s8 buf[MAX_CONF_LINE_LEN];
    u16 len;
    s8* val;

    /** Open the configuration file */
    fcfg = fopen(pfile, "r");

    if (NULL == fcfg) {
        ALOGE("%s : unable to open file \n", __func__);
        *plen = qsap_scnprintf(presp, *plen, "%s", ERR_RES_UNAVAILABLE);
        return eERR_FILE_OPEN;
    }

    /** Read the line from the configuration file */
    len = strlen(pcmd->name);
    while (NULL != fgets(buf, MAX_CONF_LINE_LEN, fcfg)) {
        s8* pline = buf;

        if (strlen(buf) == 0) continue;

        /** Skip the commented lines */
        if (buf[0] == '#') {
            if (ignore_comment) {
                pline++;
            } else
                continue;
        }

        /** Identify the configuration parameter in the configuration file */
        if (!strncmp(pline, pcmd->name, len) && (pline[len] == '=')) {
            int tmp_indx;

            /* Delate all \r \n combinations infront of the config string */
            tmp_indx = strlen(buf) - 1;
            while ((buf[tmp_indx] == '\r') || (buf[tmp_indx] == '\n')) tmp_indx--;

            buf[tmp_indx + 1] = '\0';

            if (NULL != var) {
                val = strchr(pline, '=');
                if (NULL == val) break;
                *plen = qsap_scnprintf(presp, *plen, "%s %s%s", SUCCESS, var, val);
            } else {
                *plen = qsap_scnprintf(presp, *plen, "%s %s", SUCCESS, pline);
            }
            fclose(fcfg);
            return eSUCCESS;
        }
    }

    /** Send the default value, if we are reading from ini file */
    if (pcmd->default_value) {
        *plen = qsap_scnprintf(presp, *plen, "%s %s=%s", SUCCESS, var ? var : pcmd->name,
                               pcmd->default_value);
        fclose(fcfg);
        return eSUCCESS;
    } else {
        /** Configuration parameter is absent in the file */
        *plen = qsap_scnprintf(presp, *plen, "%s", ERR_FEATURE_NOT_ENABLED);
    }

    fclose(fcfg);

    return eERR_CONFIG_PARAM_MISSING;
}

s8* qsap_get_config_value(s8* pfile, struct Command* pcmd, s8* pbuf, u32* plen) {
    s8* ptr = NULL;

    if (eSUCCESS == qsap_read_cfg(pfile, pcmd, pbuf, (u32*)plen, NULL, GET_ENABLED_ONLY)) {
        ptr = strchr(pbuf, '=');
        if (NULL != ptr) {
            ptr++;
        } else {
            ALOGE("Invalid entry, %s\n", pcmd->name);
        }
    }

    return ptr;
}

s16 wifi_qsap_reset_to_default(s8* pcfgfile, s8* pdefault) {
    FILE *fcfg, *ftmp;
    char buf[MAX_CONF_LINE_LEN];
    int status = eSUCCESS;

    fcfg = fopen(pdefault, "r");

    if (NULL == fcfg) {
        ALOGE("%s : unable to open file \n", __func__);
        return eERR_FILE_OPEN;
    }

    qsap_scnprintf(buf, sizeof(buf), "%s~", pcfgfile);

    ftmp = fopen(buf, "w+");
    if (NULL == ftmp) {
        ALOGE("%s : unable to open file \n", __func__);
        fclose(fcfg);
        return eERR_FILE_OPEN;
    }

    while (NULL != fgets(buf, MAX_CONF_LINE_LEN, fcfg)) {
        fprintf(ftmp, "%s", buf);
    }

    fclose(fcfg);
    fclose(ftmp);

    qsap_scnprintf(buf, sizeof(buf), "%s~", pcfgfile);

    if (eERR_UNKNOWN == rename(buf, pcfgfile)) status = eERR_CONF_FILE;

    /** Remove the temporary file. Dont care the return value */
    unlink(buf);

    return status;
}

static int check_for_config_file_size(FILE* fp) {
    int length = 0;

    if (NULL != fp) {
        fseek(fp, 0L, SEEK_END);
        length = ftell(fp);
    }

    return length;
}

void check_for_configuration_files(void) {
    FILE* fp;
    char* pfile;

    /* Check if configuration files are present, if not create the default files */

    /* If configuration file does not exist copy the default file */
    if (NULL == (fp = fopen(pconffile, "r"))) {
        wifi_qsap_reset_to_default(pconffile, DEFAULT_CONFIG_FILE_PATH);
    } else {
        /* The configuration file could be of 0 byte size, replace with default */
        if (check_for_config_file_size(fp) <= 0)
            wifi_qsap_reset_to_default(pconffile, DEFAULT_CONFIG_FILE_PATH);

        fclose(fp);
    }

    /* If Accept MAC list file does not exist, copy the default file */
    if (NULL == (fp = fopen(ACCEPT_LIST_FILE, "r"))) {
        wifi_qsap_reset_to_default(ACCEPT_LIST_FILE, DEFAULT_ACCEPT_LIST_FILE_PATH);
    } else {
        /* The configuration file could be of 0 byte size, replace with default */
        if (check_for_config_file_size(fp) <= 0)
            wifi_qsap_reset_to_default(ACCEPT_LIST_FILE, DEFAULT_ACCEPT_LIST_FILE_PATH);

        fclose(fp);
    }

    /* Provide read and write permissions to the owner */
    pfile = ACCEPT_LIST_FILE;
    if (chmod(pfile, 0660) < 0) {
        ALOGE("Error changing permissions of %s to 0660: %s", pfile, strerror(errno));
    }
    /* If deny MAC list file does not exist, copy the default file */
    if (NULL == (fp = fopen(DENY_LIST_FILE, "r"))) {
        wifi_qsap_reset_to_default(DENY_LIST_FILE, DEFAULT_DENY_LIST_FILE_PATH);
    } else {
        /* The configuration file could be of 0 byte size, replace with default */
        if (check_for_config_file_size(fp) <= 0)
            wifi_qsap_reset_to_default(DENY_LIST_FILE, DEFAULT_DENY_LIST_FILE_PATH);

        fclose(fp);
    }

    /* Provide read and write permissions to the owner */
    pfile = DENY_LIST_FILE;
    if (chmod(pfile, 0660) < 0) {
        ALOGE("Error changing permissions of %s to 0660: %s", pfile, strerror(errno));
    }
    return;
}
