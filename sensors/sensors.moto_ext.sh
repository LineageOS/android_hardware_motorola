#! /vendor/bin/sh
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

SOCS="4a80000.spi 998000.spi a8c000.spi a94000.spi"
SPIS="spi0.0 spi0.1"
DISPLAYS="primary secondary"
FEATURES="double_tap udfps"
SUFFIXES="enabled pressed"

set_perms_for_path() {
    [ -e "$1" ] && chown system system "$1"
}

#
# Device-based touchscreen paths (preferred)
#
for soc in $SOCS; do
    soc_base="/sys/devices/platform/soc/$soc"
    [ -d "$soc_base" ] || continue

    for spi in $SPIS; do
        spi_base="$soc_base/spi_master/spi0/$spi"
        [ -d "$spi_base" ] || continue

        for display in $DISPLAYS; do
            ts_base="$spi_base/touchscreen/$display"
            [ -d "$ts_base" ] || continue

            for feature in $FEATURES; do
                for suffix in $SUFFIXES; do
                    set_perms_for_path "$ts_base/${feature}_${suffix}"
                done
            done
        done
    done
done

#
# Class-based fallback paths
#
for display in $DISPLAYS; do
    base="/sys/class/touchscreen/$display"
    [ -d "$base" ] || continue

    for feature in $FEATURES; do
        for suffix in $SUFFIXES; do
            set_perms_for_path "$base/${feature}_${suffix}"
        done
    done
done

exit 0

