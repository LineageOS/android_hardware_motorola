#!/bin/bash

source $ANDROID_BUILD_TOP/system/tools/hidl/update-makefiles-helper.sh

do_makefiles_update \
  "vendor.goodix:hardware/motorola/interfaces/goodix"

do_makefiles_update \
  "vendor.motorola:hardware/motorola/interfaces/motorola"
