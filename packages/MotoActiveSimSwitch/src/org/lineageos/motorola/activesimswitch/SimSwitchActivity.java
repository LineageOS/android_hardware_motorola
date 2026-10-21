package org.lineageos.motorola.activesimswitch;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class SimSwitchActivity extends CollapsingToolbarBaseActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new SimSwitchFragment())
                    .commitNow();
        }
    }
}
