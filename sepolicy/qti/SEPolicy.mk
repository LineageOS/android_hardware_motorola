#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

include hardware/qcom-caf/common/qcom_defs.mk

SEPOLICY_PLATFORM := $(subst device/qcom/sepolicy_vndr/,,$(SEPOLICY_PATH))

BOARD_VENDOR_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/vendor

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/private

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/public

ifneq ($(SEPOLICY_PLATFORM), legacy-um)
BOARD_VENDOR_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/vendor/common-um

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/private/common-um

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/public/common-um
endif

ifneq ($(filter $(UM_4_4_FAMILY) $(UM_4_9_FAMILY) $(UM_4_14_FAMILY) $(UM_4_19_FAMILY) $(UM_5_4_FAMILY) $(UM_5_10_FAMILY),$(TARGET_BOARD_PLATFORM)),)
BOARD_VENDOR_SEPOLICY_DIRS += \
    hardware/motorola/sepolicy/qti/vendor/common-um-4.4-5.10
endif
