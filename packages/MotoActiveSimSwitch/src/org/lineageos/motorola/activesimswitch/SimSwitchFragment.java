package org.lineageos.motorola.activesimswitch;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

public class SimSwitchFragment extends PreferenceFragmentCompat {

    private static final String TAG = SimSwitchFragment.class.getSimpleName();

    private static final String SIM_SWITCH_PREF_KEY = "moto_active_sim_slot";

    private RilConnector mRilConnector;
    private ListPreference simPreference;
    private int activeSlot = -1;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.sim_switch_pref_screen);
        PreferenceScreen preferenceScreen = getPreferenceScreen();

        mRilConnector = new RilConnector(requireContext(), () -> {
            activeSlot = mRilConnector.getActiveSimSlot();
            Log.i(TAG, "currently active sim slot: " + activeSlot);
            simPreference.setValue("" + activeSlot);
        });

        simPreference = new ListPreference(requireContext());
        simPreference.setKey(SIM_SWITCH_PREF_KEY);
        simPreference.setTitle(R.string.sim_switch_pref_title);
        simPreference.setEntries(R.array.sim_slot_labels);
        simPreference.setEntryValues(R.array.sim_slot_numbers);

        simPreference.setOnPreferenceChangeListener((pref, newValue) -> {
            int slot = Integer.parseInt((String) newValue);
            if (slot == activeSlot) return true;

            Log.i(TAG, "switching to slot " + slot);
            if (!mRilConnector.setActiveSimSlot(slot)) {
                Log.e(TAG, "failed to switch slots");// TODO: error msg
                return false;
            }

            activeSlot = slot;
            return true;
        });

        preferenceScreen.addPreference(simPreference);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRilConnector != null)
            mRilConnector.disconnect();
    }

}
