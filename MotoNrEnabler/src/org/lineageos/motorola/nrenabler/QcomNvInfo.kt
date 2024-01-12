/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

object QcomNvInfo {
    private const val TAG = "MotoNrEnabler: QcomNvInfo"
    const val RDE_EFS_DSS_I = 10030

    interface NvDataType {
        fun serialize(buf: ByteBuffer)
        fun size(): Int
    }

    class RdeNvValue {
        var elementId = 0
        var recordNum = 0
        var offset = 0
        var length = 0
        var dataObj: NvDataType? = null
        val size: Int
            get() {
                return (dataObj?.size() ?: 1) + 16
            }
    }

    class NvGenericDataType : NvDataType {
        var data: ByteArray? = null

        constructor()
        constructor(byte: Byte) {
            data = byteArrayOf(byte)
        }

        override fun serialize(buf: ByteBuffer) {
            data?.let {
                buf.put(it)
            }
        }

        override fun size(): Int {
            return data?.size ?: 0
        }
    }

    fun getRdeByteOrder(): ByteOrder {
        return QcomOemConstants.getByteOrderByRequestId(QcomOemConstants.OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM)
    }

    fun getRdeNvName(elementId: Int): String {
        return when (elementId) {
            RDE_EFS_DSS_I -> "RDE_EFS_DSS_I"
            else -> {
                Log.w(TAG, "unknown RDE element ID: $elementId")
                ""
            }
        }
    }
}
