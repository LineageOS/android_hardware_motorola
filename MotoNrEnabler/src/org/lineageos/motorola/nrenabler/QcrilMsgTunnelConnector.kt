/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.android.internal.telephony.uicc.IccUtils
import com.qualcomm.qcrilmsgtunnel.IQcrilMsgTunnel

class QcrilMsgTunnelConnector(private val context: Context) {
    private val handler = Handler(context.mainLooper)

    private var qcrilMsgService: IQcrilMsgTunnel? = null
    private val qcrilMsgTunnelConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "QcrilMsgTunnel Service connected")
            qcrilMsgService = IQcrilMsgTunnel.Stub.asInterface(service)
            if (qcrilMsgService == null) {
                Log.e(TAG, "QcrilMsgTunnelService Connect Failed (onServiceConnected)")
                return
            }
            service.linkToDeath(qcrilMsgServiceDeathRecipient, 0)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.e(TAG, "The connection to the service got disconnected unexpectedly!")
            qcrilMsgService = null
        }
    }
    private val qcrilMsgServiceDeathRecipient = IBinder.DeathRecipient {
        Log.e(TAG, "QcrilMsgService Died")
        context.unbindService(qcrilMsgTunnelConnection)
        handler.postDelayed({ bindToQcrilMsgTunnelService() }, 4000)
    }

    init {
        bindToQcrilMsgTunnelService()
    }

    private fun bindToQcrilMsgTunnelService() {
        val intent = Intent()
        intent.setClassName(QCRIL_MSG_TUNNEL_PACKAGE_NAME, QCRIL_MSG_TUNNEL_SERVICE_NAME)
        Log.d(TAG, "Starting QcrilMsgTunnel Service")
        context.bindService(intent, qcrilMsgTunnelConnection, Context.BIND_AUTO_CREATE)
    }

    fun invokeOemRilRequestRawForPhone(phoneId: Int, oemReq: ByteArray?, oemResp: ByteArray?): Int {
        return qcrilMsgService?.let {
            Log.d(
                TAG, "invokeOemRilRequestRawForSubscriber: phoneId = $phoneId oemReq = ${
                    IccUtils.bytesToHexString(
                        oemReq
                    )
                }"
            )
            val rspData = oemResp ?: ByteArray(1)
            try {
                val ret = it.sendOemRilRequestRaw(oemReq, rspData, phoneId)
                Log.d(
                    TAG, "invokeOemRilRequestRawForSubscriber: phoneId = $phoneId oemResp = ${
                        IccUtils.bytesToHexString(rspData)
                    }"
                )
                ret
            } catch (e: RemoteException) {
                Log.e(TAG, "sendOemRilRequestRaw: Runtime Exception")
                -1
            }
        } ?: run {
            Log.e(TAG, "QcrilMsgTunnel Service not connected")
            -1
        }
    }

    private fun getRdeNvValueByElementId(
        phoneId: Int, rdeElementId: Int, recordNum: Int
    ): QcomNvInfo.RdeNvValue? {
        if (rdeElementId < 0) {
            return null
        }
        val rdeNv = QcomNvInfo.RdeNvValue()
        rdeNv.elementId = rdeElementId
        rdeNv.recordNum = recordNum
        val reqRdeData: ByteArray = QcomNvUtils.getReadingRdeNvReqData(rdeNv)
        val respRdeData: ByteArray = QcomNvUtils.allocateReadingRdeNvRespBuffer()
        return if (invokeOemRilRequestRawForPhone(
                phoneId, reqRdeData, respRdeData
            ) < 0
        ) {
            null
        } else QcomNvUtils.decodeReadingRdeNvResult(respRdeData)
    }

    fun getRdeNvValueByElementId(phoneId: Int, rdeElementId: Int): QcomNvInfo.RdeNvValue? {
        return getRdeNvValueByElementId(phoneId, rdeElementId, 0)
    }

    fun setRdeNvValue(phoneId: Int, rdeElementId: Int, value: Byte): Boolean {
        val data = QcomNvInfo.NvGenericDataType(value)
        return setRdeNvValue(phoneId, rdeElementId, data)
    }

    private fun setRdeNvValue(
        phoneId: Int, rdeElementId: Int, nvData: QcomNvInfo.NvDataType
    ): Boolean {
        return setRdeNvValue(phoneId, rdeElementId, 0, nvData)
    }

    private fun setRdeNvValue(
        phoneId: Int, rdeElementId: Int, rdeRecordNum: Int, nvData: QcomNvInfo.NvDataType
    ): Boolean {
        val nv = QcomNvInfo.RdeNvValue()
        nv.elementId = rdeElementId
        nv.recordNum = rdeRecordNum
        nv.dataObj = nvData
        return setRdeNvValue(phoneId, nv)
    }

    private fun setRdeNvValue(phoneId: Int, nv: QcomNvInfo.RdeNvValue): Boolean {
        val reqData: ByteArray = QcomNvUtils.getWritingRdeNvReqData(nv)
        val respData: ByteArray = QcomNvUtils.allocateWritingRdeNvRespBuffer()
        return getWritingRdeNvRespResult(phoneId, reqData, respData)
    }

    private fun getWritingRdeNvRespResult(
        phoneId: Int, reqData: ByteArray, respData: ByteArray
    ): Boolean {
        return getWritingNvRespResult(
            phoneId, QcomOemConstants.OEM_RIL_REQUEST_CDMA_SET_RDE_ITEM, reqData, respData
        )
    }

    private fun getWritingNvRespResult(
        phoneId: Int, reqId: Int, reqData: ByteArray, respData: ByteArray
    ): Boolean {
        if (invokeOemRilRequestRawForPhone(phoneId, reqData, respData) < 0) {
            return false
        }
        val respHeader = QcomNvUtils.readOemHookRespHeader(reqId, respData) ?: return false
        Log.d(
            TAG, "get Writing NV result for ${QcomOemConstants.getRequestName(respHeader.reqId)}"
        )
        return respHeader.error == QcomNvUtils.OemHookRespError.OEM_RIL_CDMA_SUCCESS
    }

    companion object {
        private const val TAG = "MotoNrEnabler: QcrilMsgTunnelConnector"
        private const val QCRIL_MSG_TUNNEL_PACKAGE_NAME = "com.qualcomm.qcrilmsgtunnel"
        private const val QCRIL_MSG_TUNNEL_SERVICE_NAME =
            "com.qualcomm.qcrilmsgtunnel.QcrilMsgTunnelService"
    }
}
