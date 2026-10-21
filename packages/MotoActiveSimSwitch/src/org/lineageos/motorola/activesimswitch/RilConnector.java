/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.activesimswitch;

import static org.lineageos.motorola.activesimswitch.QcomOemConstants.OEM_RIL_REQUEST_GET_ACTIVE_SIMSLOT;
import static org.lineageos.motorola.activesimswitch.QcomOemConstants.OEM_RIL_REQUEST_SWITCH_SIMSLOT;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.qualcomm.qcrilmsgtunnel.IQcrilMsgTunnel;

import java.nio.ByteBuffer;

public class RilConnector {
    private static final String TAG = RilConnector.class.getSimpleName();

    private static final String QCRIL_MSG_TUNNEL_PACKAGE_NAME = "com.qualcomm.qcrilmsgtunnel";
    private static final String QCRIL_MSG_TUNNEL_SERVICE_NAME = "com.qualcomm.qcrilmsgtunnel.QcrilMsgTunnelService";

    private static final int DEFAULT_PHONE_ID = 0;

    private final Context mContext;
    private final Runnable mOnReady;
    private final TelephonyManager mTelephonyManager;

    private IQcrilMsgTunnel mQcrilMsgTunnel = null;

    public RilConnector(Context context, Runnable onReady) {
        mContext = context;
        mOnReady = onReady;
        mTelephonyManager = context.getSystemService(TelephonyManager.class);

        Intent intent = new Intent()
                .setClassName(QCRIL_MSG_TUNNEL_PACKAGE_NAME, QCRIL_MSG_TUNNEL_SERVICE_NAME);
        context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void disconnect() {
        mContext.unbindService(mServiceConnection);
    }

    private boolean sendRequest(byte[] req, byte[] resp) {
        if (mQcrilMsgTunnel == null) {
            Log.e(TAG, "QcrilMsgTunnel not connected");
            return false;
        }

        try {
            int ret = mQcrilMsgTunnel.sendOemRilRequestRaw(req, resp, DEFAULT_PHONE_ID);
            if (ret < 0) {
                Log.e(TAG, "RIL request failed: " + ret);
                return false;
            }

            int err = Byte.toUnsignedInt(resp[0]);
            if (err != 0) {
                Log.e(TAG, "RIL error response: " + err);
                return false;
            }

            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "failed to send RIL request", e);
            return false;
        }
    }

    public int getActiveSimSlot() {
        byte[] req = ByteBuffer.allocate(8)
                .putInt(OEM_RIL_REQUEST_GET_ACTIVE_SIMSLOT)
                .putInt(0)
                .array();
        byte[] resp = new byte[2];

        if (!sendRequest(req, resp))
            return -1;

        return Byte.toUnsignedInt(resp[1]);
    }

    public boolean setActiveSimSlot(int slot) {
        byte[] req = ByteBuffer.allocate(9)
                .putInt(OEM_RIL_REQUEST_SWITCH_SIMSLOT)
                .putInt(1)   // length
                .put((byte) slot)
                .array();
        byte[] resp = new byte[]{1};

        boolean success = sendRequest(req, resp);

        if (!success) return false;
        mTelephonyManager.setSimPowerState(
                TelephonyManager.CARD_POWER_DOWN,
                Runnable::run, i -> Log.d(TAG, "sim power down result: " + i));
        return true;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "service connected: " + name);
            mQcrilMsgTunnel = IQcrilMsgTunnel.Stub.asInterface(service);
            mOnReady.run();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "service disconnected: " + name);
        }
    };

}
