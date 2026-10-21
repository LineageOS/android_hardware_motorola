/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.activesimswitch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;

public class SimSwitchActivity extends Activity {
    private static final String TAG = SimSwitchActivity.class.getSimpleName();

    private RilConnector mRilConnector;
    private int activeSlot = -1;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProgressDialog loadingDialog = ProgressDialog.show(
                this, null, getText(R.string.loading_sim_slot_picker_message),
                true, true,
                d -> finish());

        mRilConnector = new RilConnector(this, () -> {
            activeSlot = mRilConnector.getActiveSimSlot();
            Log.i(TAG, "currently active sim slot: " + activeSlot);
            loadingDialog.dismiss();
            showDialog();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRilConnector.disconnect();
    }

    private void showDialog() {
        int[] slotNumbers = getResources().getIntArray(R.array.sim_slot_numbers);

        int slotIndex = -1;
        for (int i = 0; i < slotNumbers.length; i++) {
            if (slotNumbers[i] != activeSlot) continue;
            slotIndex = i;
            break;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.sim_switch_activity_label)
                .setSingleChoiceItems(R.array.sim_slot_labels, slotIndex, (d, i) -> {
                    int newSlot = slotNumbers[i];
                    if (newSlot == activeSlot || mRilConnector.setActiveSimSlot(newSlot)) {
                        d.cancel();
                        return;
                    }

                    Log.e(TAG, "failed to switch to slot " + newSlot);
                    d.dismiss();
                    showError();
                })
                .setOnCancelListener(d -> finish())
                .show();
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sim_switch_error_title)
                .setMessage(R.string.sim_switch_error_message)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(d -> finish())
                .show();
    }

}
