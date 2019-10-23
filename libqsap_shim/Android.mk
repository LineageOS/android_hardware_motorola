#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := libqsap_shim.c
LOCAL_SHARED_LIBRARIES := libqsap_sdk liblog libcutils libutils
LOCAL_C_INCLUDES := $(TOP)/system/qcom/softap/sdk
LOCAL_MODULE := libqsap_shim
LOCAL_MODULE_TAGS := optional
LOCAL_PROPRIETARY_MODULE := true
LOCAL_VENDOR_MODULE := true
include $(BUILD_SHARED_LIBRARY)
