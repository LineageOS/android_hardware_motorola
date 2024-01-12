/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.util.Log
import java.nio.ByteOrder

object QcomOemConstants {
    const val TAG = "MotoNrEnabler: QcomOemConstants"

    private const val OEM_RIL_CDMA_MESSAGE_TYPE_CDMA = 33554432
    const val OEM_RIL_REQUEST_GET_NR_DISABLE_MODE = 327752
    const val OEM_RIL_REQUEST_SET_NR_DISABLE_MODE = 327753
    const val OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM = 33554453
    const val OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM = 33554454

    fun getByteOrderByRequestId(reqId: Int): ByteOrder {
        return if (reqId >= OEM_RIL_CDMA_MESSAGE_TYPE_CDMA) {
            ByteOrder.LITTLE_ENDIAN
        } else ByteOrder.BIG_ENDIAN
    }

    fun getRequestName(reqId: Int): String {
        return when (reqId) {
            OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM -> "OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM"
            OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM -> "OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM"
            else -> {
                Log.w(TAG, "unknown request ID: $reqId")
                ""
            }
        }
    }
}
