/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.nrenabler

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimSwitchFragment : SettingsBasePreferenceFragment() {

    private lateinit var telephonyService: QcomMotoExtTelephonyService
    private var activeSlot: Int = -1

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telephonyService = QcomMotoExtTelephonyService(requireContext())

        val loadingDialog = AlertDialog.Builder(requireContext())
            .setView(
                layoutInflater.inflate(R.layout.sim_loading_dialog, null)
            )
            .setCancelable(true)
            .setOnCancelListener { activity?.finish() }
            .create()

        loadingDialog.show()

        lifecycleScope.launch {
            val slot = withContext(Dispatchers.IO) {
                telephonyService.getActiveSimSlot(0)
            }
            Log.i(TAG, "currently active sim slot: $slot")

            if (!isAdded) return@launch
            loadingDialog.dismiss()
            activeSlot = slot
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

        AlertDialog.Builder(requireContext())
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
            .setOnCancelListener {
                activity?.finish()
            }
            .show()
    }

    private fun showError() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sim_switch_error_title)
            .setMessage(R.string.sim_switch_error_message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener {
                activity?.finish()
            }
            .show()
    }

    companion object {
        private const val TAG = "SimSwitchFragment"
    }
}
