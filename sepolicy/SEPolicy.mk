#
# Copyright (C) The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

SEPOLICY_PATH := hardware/motorola/sepolicy

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    $(SEPOLICY_PATH)/common/system_ext/private

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    $(SEPOLICY_PATH)/common/system_ext/public

BOARD_VENDOR_SEPOLICY_DIRS += \
    $(SEPOLICY_PATH)/common/vendor
