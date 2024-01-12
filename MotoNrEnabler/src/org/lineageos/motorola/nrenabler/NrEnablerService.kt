/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class NrEnablerService : Service() {
    private lateinit var motoExtService: QcomMotoExtTelephonyService
    private val handler by lazy { Handler(mainLooper) }
    private val workingInProgress = AtomicBoolean(false)

    private val repeatWorkOnNRModeAndDSSIfFail = object : Runnable {
        override fun run() {
            if (workingInProgress.getAndSet(true))
                return
            if (!workOnNRModeAndDSS()) {
                Log.v(TAG, "workOnNRModeAndDSS failed, retry after 5s")
                handler.removeCallbacks(this)
                handler.postDelayed(this, 5000)
            }
            workingInProgress.set(false)
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!workingInProgress.get()) {
                handler.post(repeatWorkOnNRModeAndDSSIfFail)
            }
        }
    }

    override fun onCreate() {
        motoExtService = QcomMotoExtTelephonyService(this)
        registerReceiver(
            broadcastReceiver, IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)
        )
    }

    private fun workOnNRModeAndDSS(): Boolean {
        val activeSubs =
            getSystemService(SubscriptionManager::class.java)?.getActiveSubscriptionInfoList()
        if (activeSubs.isNullOrEmpty()) {
            Log.v(TAG, "workOnNRModeAndDSS: no active sub.")
            return true
        }
        for (aSubInfo in activeSubs) {
            val phoneId = SubscriptionManager.getPhoneId(aSubInfo.subscriptionId)
            if (!validatePhoneId(phoneId)) {
                Log.e(TAG, "Invalid phoneId: $phoneId")
                return false
            }

            // Moto sets them based on carrier config, but we unconditionally
            // enable NR and DSS here because maintaining carrier config is
            // intractable for us.
            Log.v(TAG, "workOnNRModeAndDSS: setNrModeDisabled for phone ${phoneId}")
            if (!motoExtService.setNrModeDisabled(phoneId, NrMode.AUTO)) {
                return false
            }
            Log.v(TAG, "workOnNRModeAndDSS: setDSSEnabled for phone ${phoneId}")
            if (!motoExtService.setDSSEnabled(phoneId, 1.toByte())) {
                return false
            }
        }
        return true
    }

    private fun validatePhoneId(phoneId: Int): Boolean {
        val phoneCount = getSystemService(TelephonyManager::class.java).activeModemCount
        return phoneId in 0 until phoneCount
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MotoNrEnabler"
    }
}
