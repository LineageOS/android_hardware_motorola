/*
 * SPDX-FileCopyrightText: 2010 The Linux Foundation
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: BSD-3-Clause
 */

#ifndef _QSAP_API_H_
#define _QSAP_API_H_

#define LOG_TAG "QCSDK"
#include <log/log.h>

typedef unsigned char u8;
typedef char s8;
typedef unsigned short int u16;
typedef signed short int s16;
typedef unsigned int u32;
typedef signed int s32;

/** Success and error messages */
#define SUCCESS "success"
#define ERR_FEATURE_NOT_ENABLED "failure feature not enabled"
#define ERR_RES_UNAVAILABLE "failure resource unavailable"

/** Error numbers used with the SDK */
enum error_val {
    eERR_UNKNOWN = -1,
    eSUCCESS = 0,
    eERR_STOP_BSS,
    eERR_BSS_NOT_STARTED,
    eERR_COMMIT,
    eERR_START_SAP,
    eERR_STOP_SAP,
    eERR_RELOAD_SAP,
    eERR_FILE_OPEN,
    eERR_CONF_FILE,
    eERR_INVALID_MAC_ADDR,
    eERR_SEND_TO_HOSTAPD,
    eERR_CONFIG_PARAM_MISSING,
    eERR_CHAN_READ,
    eERR_FEATURE_NOT_ENABLED,
    eERR_UNLOAD_FAILED_SDIO,
    eERR_UNLOAD_FAILED_SOFTAP,
    eERR_LOAD_FAILED_SDIOIF,
    eERR_LOAD_FAILED_SOFTAP,
    eERR_SET_CHAN_RANGE,
    eERR_GET_AUTO_CHAN,
    eERR_SET_TX_POWER
};

/** Configuration file name */
#define CONFIG_FILE "/data/vendor/wifi/hostapd/hostapd.conf"

/** Default configuration file path */
#define DEFAULT_CONFIG_FILE_PATH "/vendor/etc/hostapd/hostapd_default.conf"

/** Default Accept list file name */
#define DEFAULT_ACCEPT_LIST_FILE_PATH "/vendor/etc/hostapd/hostapd.accept"

/** Accept list file name */
#define ACCEPT_LIST_FILE "/data/vendor/wifi/hostapd/hostapd.accept"

/** Default Deny list file name */
#define DEFAULT_DENY_LIST_FILE_PATH "/vendor/etc/hostapd/hostapd.deny"

/** Deny list file name */
#define DENY_LIST_FILE "/data/vendor/wifi/hostapd/hostapd.deny"

/** Maximum length of the line in the configuration file */
#define MAX_CONF_LINE_LEN (156)

/** parameters for read config */
#define GET_ENABLED_ONLY 0

/**
 * Command numbers, these numbers form the index into the array of
 * command names stored in the 'cmd_list'.
 *
 * Warning: An addtion of an entry in 'esap_cmd', should be followed
 * by an addition of a command name string in the 'cmd_list' array
 */
typedef enum esap_cmd {
    eCMD_INVALID = -1,
    eCMD_SSID = 0,
    eCMD_BSSID = 1,
    eCMD_CHAN = 2,
    eCMD_BCN_INTERVAL = 3,
    eCMD_DTIM_PERIOD = 4,
    eCMD_HW_MODE = 5,
    eCMD_AUTH_ALGS = 6,
    eCMD_SEC_MODE = 7,
    eCMD_WEP_KEY0 = 8,
    eCMD_WEP_KEY1 = 9,
    eCMD_WEP_KEY2 = 10,
    eCMD_WEP_KEY3 = 11,
    eCMD_DEFAULT_KEY = 12,
    eCMD_PASSPHRASE = 13,
    eCMD_WPA_PAIRWISE = 14,
    eCMD_RSN_PAIRWISE = 15,
    eCMD_MAC_ADDR = 16,
    eCMD_RESET_AP = 17,
    eCMD_MAC_ACL = 18,
    eCMD_ADD_TO_ALLOW = 19,
    eCMD_ADD_TO_DENY = 20,
    eCMD_REMOVE_FROM_ALLOW = 21,
    eCMD_REMOVE_FROM_DENY = 22,
    eCMD_ALLOW_LIST = 23,
    eCMD_DENY_LIST = 24,
    eCMD_COMMIT = 25,
    eCMD_ENABLE_SOFTAP = 26,
    eCMD_DISASSOC_STA = 27,
    eCMD_RESET_TO_DEFAULT = 28,
    eCMD_PROTECTION_FLAG = 29,
    eCMD_DATA_RATES = 30,
    eCMD_ASSOC_STA_MACS = 31,
    eCMD_TX_POWER = 32,
    eCMD_SDK_VERSION = 33,
    eCMD_WMM_STATE = 34,

    /** WARNING: The order of WPS commands should not be altered.
        New commands SHOULD be added above or below this            */
    eCMD_WPS_STATE = 35,
    eCMD_WPS_CONFIG_METHOD = 36,
    eCMD_UUID = 37,
    eCMD_DEVICE_NAME = 38,
    eCMD_MANUFACTURER = 39,
    eCMD_MODEL_NAME = 40,
    eCMD_MODEL_NUMBER = 41,
    eCMD_SERIAL_NUMBER = 42,
    eCMD_DEVICE_TYPE = 43,
    eCMD_OS_VERSION = 44,
    eCMD_FRIENDLY_NAME = 45,
    eCMD_MANUFACTURER_URL = 46,
    eCMD_MODEL_DESC = 47,
    eCMD_MODEL_URL = 48,
    eCMD_UPC = 49,
    /******************************************************/

    eCMD_FRAG_THRESHOLD = 50,
    eCMD_RTS_THRESHOLD = 51,
    eCMD_GTK_TIMEOUT = 52,
    eCMD_COUNTRY_CODE = 53,
    eCMD_INTRA_BSS_FORWARD = 54,
    eCMD_REGULATORY_DOMAIN = 55,
    eCMD_AP_STATISTICS = 56,
    eCMD_AP_AUTOSHUTOFF = 57,
    eCMD_AP_ENERGY_DETECT_TH = 58,
    eCMD_BASIC_RATES = 59,
    eCMD_REQUIRE_HT = 60,
    eCMD_IEEE80211N = 61,
    eCMD_SET_CHANNEL_RANGE = 62,
    eCMD_GET_AUTO_CHANNEL = 63,
    eCMD_IEEE80211W = 64,
    eCMD_WPA_KEY_MGMT = 65,
    eCMD_SET_MAX_CLIENTS = 66,
    eCMD_IEEE80211AC = 67,
    eCMD_VHT_OPER_CH_WIDTH = 68,
    eCMD_ACS_CHAN_LIST = 69,
    eCMD_HT_CAPAB = 70,
    eCMD_IEEE80211H = 71,

    eCMD_ENABLE_WIGIG_SOFTAP = 72,
    eCMD_INTERFACE = 73,
    eCMD_SSID2 = 74,
    eCMD_BRIDGE = 75,
    eCMD_CTRL_INTERFACE = 76,
    eCMD_VENDOR_ELEMENT = 77,
    eCMD_ASSOCRESP_ELEMENT = 78,
    eCMD_ACS_EXCLUDE_DFS = 79,
    eCMD_WOWLAN_TRIGGERS = 80,
    eCMD_ACCEPT_MAC_FILE = 81,
    eCMD_DENY_MAC_FILE = 82,
    eCMD_OWE_TRANS_IFNAME = 83,
    eCMD_SAE_REQUIRE_MPF = 84,

    eCMD_IEEE80211AX = 85,

    eCMD_ENABLE_EDMG = 86,
    eCMD_EDMG_CHANNEL = 87,

    eCMD_LAST /** New command numbers should be added above this */
} esap_cmd_t;

/** non-commands */
typedef enum esap_str {
    STR_WPA = 0,
    STR_ACCEPT_MAC_FILE = 1,
    STR_DENY_MAC_FILE = 2,
    STR_MAC_IN_INI = 3,
    STR_PROT_FLAG_IN_INI = 4,
    STR_DATA_RATE_IN_INI = 5,
    STR_TX_POWER_IN_INI = 6,
    STR_FRAG_THRESHOLD_IN_INI = 7,
    STR_RTS_THRESHOLD_IN_INI = 8,
    STR_COUNTRY_CODE_IN_INI = 9,
    STR_INTRA_BSS_FORWARD_IN_INI = 10,
    STR_WMM_IN_INI = 11,
    STR_802DOT11D_IN_INI = 12,
    STR_HT_80211N = 13,
    STR_CTRL_INTERFACE = 14,
    STR_INTERFACE = 15,
    STR_EAP_SERVER = 16,
    STR_AP_AUTOSHUTOFF = 17,
    STR_AP_ENERGY_DETECT_TH = 18,
    eSTR_LAST
} esap_str_t;

enum { DISABLE = 0, ENABLE = 1 };

struct Command {
    s8* name;
    s8* default_value;
};

/** Function declartion */
s32 is_softap_enabled(void);
s8* qsap_get_config_value(s8* pfile, struct Command* pcmd, s8* pbuf, u32* plen);
int qsap_get_mode(s32* pmode);

#endif
