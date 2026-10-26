/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import java.nio.ByteBuffer

class QcomMotoExtTelephonyService(private val context: Context) {
    private val qcrilMsgTunnelConnector = QcrilMsgTunnelConnector(context)

    fun setNrModeDisabled(phoneId: Int, mode: NrMode): Boolean {
        val nrModeInModem = getNrModeDisabled(phoneId)
        Log.v(TAG, "nrModeInModem = $nrModeInModem")
        if (mode == nrModeInModem) {
            Log.d(
                TAG,
                "setNrModeDisabled equals nrModeInModem:$nrModeInModem, ignore set for phoneID:$phoneId",
            )
            return true
        }
        val data = ByteArray(9)
        val buf = ByteBuffer.wrap(data)
        buf.order(
            QcomOemConstants.getByteOrderByRequestId(
                QcomOemConstants.OEM_RIL_REQUEST_SET_NR_DISABLE_MODE
            )
        )
        buf.putInt(QcomOemConstants.OEM_RIL_REQUEST_SET_NR_DISABLE_MODE)
            .putInt(1)
            .put(mode.toInt().toByte())
        return qcrilMsgTunnelConnector.invokeOemRilRequestRawForPhone(phoneId, data, null) >= 0
    }

    private fun getNrModeDisabled(phoneId: Int): NrMode? {
        val data = ByteArray(8)
        val respData = ByteArray(1)
        val buf = ByteBuffer.wrap(data)
        buf.order(
            QcomOemConstants.getByteOrderByRequestId(
                QcomOemConstants.OEM_RIL_REQUEST_GET_NR_DISABLE_MODE
            )
        )
        buf.putInt(QcomOemConstants.OEM_RIL_REQUEST_GET_NR_DISABLE_MODE)
        if (qcrilMsgTunnelConnector.invokeOemRilRequestRawForPhone(phoneId, data, respData) >= 0) {
            return NrMode.fromInt(respData[0].toInt())
        }
        return null
    }

    fun getActiveSimSlot(phoneId: Int): Int {
        val data = ByteArray(8)
        val respData = ByteArray(2)
        val buf = ByteBuffer.wrap(data)
        buf.order(
            QcomOemConstants.getByteOrderByRequestId(
                QcomOemConstants.OEM_RIL_REQUEST_GET_ACTIVE_SIMSLOT
            )
        )
        buf.putInt(QcomOemConstants.OEM_RIL_REQUEST_GET_ACTIVE_SIMSLOT)

        val ret = qcrilMsgTunnelConnector.invokeOemRilRequestRawForPhone(phoneId, data, respData)
        if (ret < 0) {
            Log.e(TAG, "RIL request failed: $ret")
            return -1
        }

        val err = respData[0].toInt() and 0xFF
        if (err != 0) {
            Log.e(TAG, "RIL error response: $err")
            return -1
        }

        return respData[1].toInt() and 0xFF
    }

    fun setActiveSimSlot(phoneId: Int, slot: Int): Boolean {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        val data = ByteArray(9)
        val respData = ByteArray(1)
        val buf = ByteBuffer.wrap(data)
        buf.order(
            QcomOemConstants.getByteOrderByRequestId(
                QcomOemConstants.OEM_RIL_REQUEST_SWITCH_SIMSLOT
            )
        )
        buf.putInt(QcomOemConstants.OEM_RIL_REQUEST_SWITCH_SIMSLOT).putInt(1).put(slot.toByte())

        val ret = qcrilMsgTunnelConnector.invokeOemRilRequestRawForPhone(phoneId, data, respData)
        if (ret < 0) {
            Log.e(TAG, "RIL request failed: $ret")
            return false
        }

        val err = respData[0].toInt() and 0xFF
        if (err != 0) {
            Log.e(TAG, "RIL error response: $err")
            return false
        }

        telephonyManager.setSimPowerState(
            TelephonyManager.CARD_POWER_DOWN,
            { obj: Runnable? -> obj!!.run() },
            { i: Int? -> Log.d(TAG, "sim power down result: $i") },
        )

        return true
    }

    private fun getDSSEnabled(phoneId: Int): Byte {
        val rdeNv =
            qcrilMsgTunnelConnector.getRdeNvValueByElementId(phoneId, QcomNvInfo.RDE_EFS_DSS_I)
        return (rdeNv?.dataObj as QcomNvInfo.NvGenericDataType?)?.data?.get(0) ?: 2.toByte()
    }

    fun setDSSEnabled(phoneId: Int, enabled: Byte): Boolean {
        val prev = getDSSEnabled(phoneId)
        Log.v(TAG, "previous DSS mode = $prev")
        if (prev == enabled) {
            Log.d(TAG, "Skip setDSSEnabled as no change.")
            return true
        }
        return qcrilMsgTunnelConnector.setRdeNvValue(phoneId, QcomNvInfo.RDE_EFS_DSS_I, enabled)
    }

    companion object {
        private const val TAG = "MotoNrEnabler: QcomMotoExtTelephonyService"
    }
}
