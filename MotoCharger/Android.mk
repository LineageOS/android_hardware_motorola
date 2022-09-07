#
# Copyright (C) 2021 The LineageOS Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

# Set MotoCharger_Resolution to the resolution of the device.
ifneq (,$(filter 540x960 720x1280 720x1440 720x1512 720x1570 876x2142 1080x1920 1080x2160 1080x2246 1080x2270 1080x2340 1080x2400 1080x2520 1440x2560,$(MOTO_CHARGER_RES)))
MotoCharger_Resolution := $(MOTO_CHARGER_RES)
endif

include $(CLEAR_VARS)
LOCAL_MODULE := MotoCharger_battery_scale
LOCAL_MODULE_STEM := battery_scale.png
LOCAL_SRC_FILES := $(MotoCharger_Resolution)/battery_scale.png
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/res/images/charger
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := MotoCharger_battery_fail
LOCAL_MODULE_STEM := battery_fail.png
LOCAL_SRC_FILES := $(MotoCharger_Resolution)/battery_fail.png
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/res/images/charger
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := MotoCharger_animation
LOCAL_MODULE_STEM := animation.txt
LOCAL_SRC_FILES := animation.txt
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/res/values/charger
LOCAL_REQUIRED_MODULES := MotoCharger_battery_scale MotoCharger_battery_fail
include $(BUILD_PREBUILT)
