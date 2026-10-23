/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimSwitchActivity : CollapsingToolbarBaseActivity() {

    private lateinit var telephonyService: QcomMotoExtTelephonyService
    private lateinit var loadingDialog: AlertDialog
    private var activeSlot: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telephonyService = QcomMotoExtTelephonyService(this)

        loadingDialog =
            AlertDialog.Builder(this)
                .setView(layoutInflater.inflate(R.layout.sim_loading_dialog, null))
                .setCancelable(true)
                .setOnCancelListener { finish() }
                .show()

        lifecycleScope.launch {
            val deadlineTs: Long = SystemClock.elapsedRealtime() + LOADING_TIMEOUT
            while (activeSlot == -1 && SystemClock.elapsedRealtime() < deadlineTs) {
                delay(LOADING_POLL_INTERVAL)
                if (isFinishing or isDestroyed) return@launch

                activeSlot = withContext(Dispatchers.IO) { telephonyService.getActiveSimSlot(0) }
            }

            Log.i(TAG, "currently active sim slot: $activeSlot")

            loadingDialog.dismiss()
            showSlotChoiceDialog()
        }
    }

    private fun showSlotChoiceDialog() {
        val slotNumbers = resources.getIntArray(R.array.sim_slot_numbers)
        val slotLabels = resources.getStringArray(R.array.sim_slot_labels)

        var selectedIndex = -1
        for (i in slotNumbers.indices) {
            if (slotNumbers[i] == activeSlot) {
                selectedIndex = i
                break
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.sim_switch_activity_label)
            .setSingleChoiceItems(slotLabels, selectedIndex) { dialog, which ->
                val newSlot = slotNumbers[which]
                if (newSlot == activeSlot || telephonyService.setActiveSimSlot(0, newSlot)) {
                    dialog.cancel()
                    return@setSingleChoiceItems
                }

                Log.e(TAG, "failed to switch to slot $newSlot")
                dialog.dismiss()
                showError()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showError() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sim_switch_error_title)
            .setMessage(R.string.sim_switch_error_message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { finish() }
            .show()
    }

    companion object {
        private const val TAG = "SimSwitchActivity"
        private const val LOADING_TIMEOUT = 5000L
        private const val LOADING_POLL_INTERVAL = 200L
    }
}
