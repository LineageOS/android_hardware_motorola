/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.motorola.activesimswitch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.telephony.TelephonyManager
import android.util.Log
import com.qualcomm.qcrilmsgtunnel.IQcrilMsgTunnel
import java.lang.Byte
import java.nio.ByteBuffer
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.Int
import kotlin.String
import kotlin.byteArrayOf

class RilConnector(private val mContext: Context, private val mOnReady: Runnable) {
    private val mTelephonyManager: TelephonyManager? =
        mContext.getSystemService(TelephonyManager::class.java)

    private var mQcrilMsgTunnel: IQcrilMsgTunnel? = null

    fun disconnect() {
        mContext.unbindService(mServiceConnection)
    }

    private fun sendRequest(req: ByteArray?, resp: ByteArray): Boolean {
        if (mQcrilMsgTunnel == null) {
            Log.e(TAG, "QcrilMsgTunnel not connected")
            return false
        }

        try {
            val ret = mQcrilMsgTunnel!!.sendOemRilRequestRaw(req, resp, DEFAULT_PHONE_ID)
            if (ret < 0) {
                Log.e(TAG, "RIL request failed: $ret")
                return false
            }

            val err = Byte.toUnsignedInt(resp[0])
            if (err != 0) {
                Log.e(TAG, "RIL error response: $err")
                return false
            }

            return true
        } catch (e: RemoteException) {
            Log.e(TAG, "failed to send RIL request", e)
            return false
        }
    }

    fun getActiveSimSlot(): Int {
        val req = ByteBuffer.allocate(8)
            .putInt(QcomOemConstants.OEM_RIL_REQUEST_GET_ACTIVE_SIMSLOT)
            .putInt(0)
            .array()
        val resp = ByteArray(2)

        if (!sendRequest(req, resp)) return -1

        return Byte.toUnsignedInt(resp[1])
    }

    fun setActiveSimSlot(slot: Int): Boolean {
        val req = ByteBuffer.allocate(9)
            .putInt(QcomOemConstants.OEM_RIL_REQUEST_SWITCH_SIMSLOT)
            .putInt(1) // length
            .put(slot.toByte())
            .array()
        val resp = byteArrayOf(1)

        val success = sendRequest(req, resp)

        if (!success) return false
        mTelephonyManager!!.setSimPowerState(
            TelephonyManager.CARD_POWER_DOWN,
            { obj: Runnable? -> obj!!.run() },
            { i: Int? -> Log.d(TAG, "sim power down result: $i") })
        return true
    }

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "service connected: $name")
            mQcrilMsgTunnel = IQcrilMsgTunnel.Stub.asInterface(service)
            mOnReady.run()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "service disconnected: $name")
        }
    }

    init {

        val intent: Intent = Intent()
            .setClassName(QCRIL_MSG_TUNNEL_PACKAGE_NAME, QCRIL_MSG_TUNNEL_SERVICE_NAME)
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    companion object {
        private val TAG: String = RilConnector::class.java.getSimpleName()

        private const val QCRIL_MSG_TUNNEL_PACKAGE_NAME = "com.qualcomm.qcrilmsgtunnel"
        private const val QCRIL_MSG_TUNNEL_SERVICE_NAME =
            "com.qualcomm.qcrilmsgtunnel.QcrilMsgTunnelService"

        private const val DEFAULT_PHONE_ID = 0
    }
}
