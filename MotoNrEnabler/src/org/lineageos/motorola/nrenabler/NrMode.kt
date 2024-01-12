/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

enum class NrMode(private val id: Int) {
    AUTO(0),
    DISABLE_SA(1),
    DISABLE_NSA(2);

    fun toInt(): Int {
        return id
    }

    companion object {
        fun fromInt(id: Int): NrMode? {
            for (en in NrMode.values()) {
                if (en.id == id) {
                    return en
                }
            }
            return null
        }
    }
}
