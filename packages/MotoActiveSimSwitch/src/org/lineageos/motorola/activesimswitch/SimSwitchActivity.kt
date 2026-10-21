/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.motorola.activesimswitch

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log

class SimSwitchActivity : Activity() {
    private var mRilConnector: RilConnector? = null
    private var activeSlot = -1

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loadingDialog = ProgressDialog.show(
            this, null, getText(R.string.loading_sim_slot_picker_message),
            true, true
        ) { _: DialogInterface? -> finish() }

        mRilConnector = RilConnector(this) {
            activeSlot = mRilConnector!!.getActiveSimSlot()
            Log.i(TAG, "currently active sim slot: $activeSlot")
            loadingDialog.dismiss()
            showDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRilConnector!!.disconnect()
    }

    private fun showDialog() {
        val slotNumbers = getResources().getIntArray(R.array.sim_slot_numbers)

        var slotIndex = -1
        for (i in slotNumbers.indices) {
            if (slotNumbers[i] != activeSlot) continue
            slotIndex = i
            break
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.sim_switch_activity_label)
            .setSingleChoiceItems(R.array.sim_slot_labels, slotIndex) { d: DialogInterface?, i: Int ->
                val newSlot = slotNumbers[i]
                if (newSlot == activeSlot || mRilConnector!!.setActiveSimSlot(newSlot)) {
                    d!!.cancel()
                    return@setSingleChoiceItems
                }

                Log.e(TAG, "failed to switch to slot $newSlot")
                d!!.dismiss()
                showError()
            }
            .setOnCancelListener { _: DialogInterface? -> finish() }
            .show()
    }

    private fun showError() {
        AlertDialog.Builder(this)
            .setTitle(R.string.sim_switch_error_title)
            .setMessage(R.string.sim_switch_error_message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { _: DialogInterface? -> finish() }
            .show()
    }

    companion object {
        private val TAG: String = SimSwitchActivity::class.java.getSimpleName()
    }
}
