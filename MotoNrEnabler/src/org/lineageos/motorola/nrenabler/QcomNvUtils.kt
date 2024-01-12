/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.util.Log
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer


object QcomNvUtils {
    private const val TAG = "MotoNrEnabler: QcomNvUtils"

    private const val DEFAULT_SPC_CODE = "000000"
    private const val READING_RDE_RESP_BUF_SIZE = 6144
    private const val WRITING_RESP_BUF_SIZE = 2048

    data class OemHookDataHeader(
        val reqId: Int,
        val dataLength: Int,
        val error: OemHookRespError,
    ) {
        val spcLockCode = ByteArray(6)
        override fun toString(): String {
            return "reqId = $reqId  dataLength = $dataLength   error = $error  spcLockCode = ${
                byteArrToStringLog(
                    spcLockCode
                )
            }"
        }

        companion object {
            const val SIZE = 18
        }
    }

    fun readOemHookRespHeader(reqId: Int, bytes: ByteArray?): OemHookDataHeader? {
        return bytes?.let {
            readOemHookRespHeader(
                ByteBuffer.wrap(it).order(QcomOemConstants.getByteOrderByRequestId(reqId))
            )
        }
    }

    private fun readOemHookRespHeader(buf: ByteBuffer): OemHookDataHeader? {
        return try {
            val header = OemHookDataHeader(
                buf.getInt(),
                buf.getInt(),
                OemHookRespError.fromInt(buf.getInt()),
            )
            for (i in 0 until header.spcLockCode.size) {
                header.spcLockCode[i] = buf.get()
            }
            Log.d(TAG, "readOemHookRespHeader: $header")
            header
        } catch (e: BufferUnderflowException) {
            Log.w(TAG, "decode RespHeader exception, BufferUnderflowException")
            null
        }
    }

    fun getReadingRdeNvReqData(rdeNv: QcomNvInfo.RdeNvValue): ByteArray {
        return allocateRdeOemReqData(
            QcomOemConstants.OEM_RIL_REQUEST_CDMA_GET_RDE_ITEM, rdeNv, DEFAULT_SPC_CODE
        )
    }

    fun getWritingRdeNvReqData(rdeNv: QcomNvInfo.RdeNvValue): ByteArray {
        return allocateRdeOemReqData(
            QcomOemConstants.OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, rdeNv, DEFAULT_SPC_CODE
        )
    }

    private fun allocateRdeOemReqData(
        reqId: Int, rdeNv: QcomNvInfo.RdeNvValue, spcCode: String
    ): ByteArray {
        val buf = ByteBuffer.allocate(rdeNv.size + OemHookDataHeader.SIZE)
        buf.order(QcomNvInfo.getRdeByteOrder())
        writeOemHookReqHeader(
            buf, reqId, rdeNv.size, OemHookRespError.OEM_RIL_CDMA_SUCCESS, spcCode
        )
        buf.putInt(rdeNv.elementId)
        buf.putInt(rdeNv.recordNum)
        buf.putInt(rdeNv.offset)
        rdeNv.dataObj.let {
            if (it != null) {
                buf.putInt(it.size())
                it.serialize(buf)
            } else {
                buf.putInt(0)
                buf.put(0.toByte())
            }
        }
        val data = buf.array()
        Log.d(
            TAG,
            "RDE request for element: ${QcomNvInfo.getRdeNvName(rdeNv.elementId)}  Allocated OemReqData: data = ${
                byteArrToStringLog(
                    data
                )
            }"
        )
        return data
    }

    fun allocateReadingRdeNvRespBuffer(): ByteArray {
        return ByteArray(READING_RDE_RESP_BUF_SIZE)
    }

    fun allocateWritingRdeNvRespBuffer(): ByteArray {
        return ByteArray(WRITING_RESP_BUF_SIZE)
    }

    fun decodeReadingRdeNvResult(resultData: ByteArray?): QcomNvInfo.RdeNvValue? {
        if (resultData == null) {
            return null
        }
        val buf = ByteBuffer.wrap(resultData).order(QcomNvInfo.getRdeByteOrder())
        return try {
            val header = readOemHookRespHeader(buf)
            if (header != null && header.error === OemHookRespError.OEM_RIL_CDMA_SUCCESS) {
                return deserializeRde(buf)
            }
            Log.w(TAG, "decodeReadingRdeNv get error for head")
            null
        } catch (e: BufferUnderflowException) {
            Log.e(TAG, "decodeReadingRdeNvResult: buffer underflow")
            null
        }
    }

    private fun deserializeRde(buf: ByteBuffer): QcomNvInfo.RdeNvValue {
        val rdeNv = QcomNvInfo.RdeNvValue()
        rdeNv.elementId = buf.getInt()
        rdeNv.recordNum = buf.getInt()
        rdeNv.offset = buf.getInt()
        rdeNv.length = buf.getInt()

        Log.d(TAG, "decoding response for ${QcomNvInfo.getRdeNvName(rdeNv.elementId)}")

        when (rdeNv.elementId) {
            QcomNvInfo.RDE_EFS_DSS_I -> {
                if (rdeNv.length > 0) {
                    val nvData = QcomNvInfo.NvGenericDataType()
                    nvData.data = buf.array().copyOfRange(34, 34 + rdeNv.length)
                    rdeNv.dataObj = nvData
                }
            }

            else -> Log.d(TAG, "deserialize unknown elementId (${rdeNv.elementId})")
        }
        return rdeNv
    }

    fun byteArrToStringLog(arr: ByteArray?): String {
        if (arr == null || arr.isEmpty()) {
            return "null"
        }
        val sb = StringBuilder()
        for (i in arr) {
            sb.append(String.format("%02X", i))
        }
        return sb.toString()
    }

    private fun writeOemHookReqHeader(
        buf: ByteBuffer, reqId: Int, len: Int, err: OemHookRespError, spcLockCode: String
    ) {
        writeOemHookReqHeader(buf, reqId, len, err, spcLockCode.toByteArray())
    }

    private fun writeOemHookReqHeader(
        buf: ByteBuffer, reqId: Int, len: Int, err: OemHookRespError, spcLockCode: ByteArray
    ) {
        buf.putInt(reqId)
        buf.putInt(len)
        buf.putInt(err.toInt())
        for (i in spcLockCode) {
            buf.put(i)
        }
        Log.d(
            TAG,
            "writeOemHookReqHeader: reqId = $reqId  dataLength = $len  error = $err  spcLockCode = ${
                byteArrToStringLog(
                    spcLockCode
                )
            }"
        )
    }

    enum class OemHookRespError(private val id: Int) {
        OEM_RIL_CDMA_SUCCESS(0),
        OEM_RIL_CDMA_RADIO_NOT_AVAILABLE(1),
        OEM_RIL_CDMA_NAM_READ_WRITE_FAILURE(2),
        OEM_RIL_CDMA_NAM_PASSWORD_INCORRECT(3),
        OEM_RIL_CDMA_NAM_ACCESS_COUNTER_EXCEEDED(4),
        OEM_RIL_CDMA_GENERIC_FAILURE(5);

        fun toInt(): Int {
            return id
        }

        companion object {
            fun fromInt(id: Int): OemHookRespError {
                for (en in values()) {
                    if (en.id == id) {
                        return en
                    }
                }
                return OEM_RIL_CDMA_GENERIC_FAILURE
            }
        }
    }
}
