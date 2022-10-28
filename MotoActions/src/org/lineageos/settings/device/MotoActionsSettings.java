/*
 * Copyright (c) 2015 The CyanogenMod Project
 * Copyright (c) 2017-2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.device;

import static android.provider.Settings.Secure.DOZE_ALWAYS_ON;
import static android.provider.Settings.Secure.DOZE_ENABLED;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.AmbientDisplayConfiguration;
import android.os.UserHandle;
import android.provider.Settings;

import androidx.preference.PreferenceManager;

import org.lineageos.settings.device.actions.TorchAction;
import org.lineageos.settings.device.actions.UpdatedStateNotifier;

public class MotoActionsSettings {
    private static final String GESTURE_CHOP_CHOP_KEY = "gesture_chop_chop";
    private static final String GESTURE_IR_SILENCER_KEY = "gesture_ir_silencer";
    private static final String GESTURE_FLIP_TO_MUTE_KEY = "gesture_flip_to_mute";
    private static final String GESTURE_LIFT_TO_SILENCE_KEY = "gesture_lift_to_silence";

    static final String GESTURE_IR_WAKEUP_KEY = "gesture_hand_wave";
    static final String GESTURE_PICK_UP_KEY = "gesture_pick_up";
    static final String GESTURE_POCKET_KEY = "gesture_pocket";

    static final String DOZE_ENABLE = "doze_enable";
    static final String ALWAYS_ON_DISPLAY = "always_on_display";

    private final Context mContext;
    private final UpdatedStateNotifier mUpdatedStateNotifier;

    private boolean mChopChopEnabled;
    private boolean mPickUpGestureEnabled;
    private boolean mPocketGestureEnabled;
    private boolean mIrWakeUpEnabled;
    private boolean mIrSilencerEnabled;
    private boolean mFlipToMuteEnabled;
    private boolean mLiftToSilenceEnabled;

    public MotoActionsSettings(Context context, UpdatedStateNotifier updatedStateNotifier) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        loadPreferences(sharedPrefs);
        sharedPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
        mContext = context;
        mUpdatedStateNotifier = updatedStateNotifier;
    }

    public boolean isChopChopGestureEnabled() {
        return mChopChopEnabled;
    }

    public static boolean isAlwaysOnEnabled(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                DOZE_ALWAYS_ON, 0, UserHandle.USER_CURRENT) != 0;
    }

    public static boolean alwaysOnDisplayAvailable(Context context) {
        return new AmbientDisplayConfiguration(context).alwaysOnAvailable();
    }

    public static boolean enableAlwaysOn(Context context, boolean enable) {
        return Settings.Secure.putIntForUser(context.getContentResolver(),
                DOZE_ALWAYS_ON, enable ? 1 : 0, UserHandle.USER_CURRENT);
    }

    public static boolean enableDoze(Context context, boolean enable) {
        return Settings.Secure.putInt(context.getContentResolver(),
                DOZE_ENABLED, enable ? 1 : 0);
    }

    public static boolean isDozeEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                DOZE_ENABLED, 1) != 0;
    }

    public boolean isAlwaysOnEnabled() {
        return isAlwaysOnEnabled(mContext);
    }

    public boolean isDozeEnabled() {
        return isDozeEnabled(mContext);
    }

    public boolean isIrWakeupEnabled() {
        return isDozeEnabled() && !isAlwaysOnEnabled() && mIrWakeUpEnabled;
    }

    public boolean isPickUpEnabled() {
        return isDozeEnabled() && !isAlwaysOnEnabled() && mPickUpGestureEnabled;
    }

    public boolean isPocketGestureEnabled() {
        return isDozeEnabled() && !isAlwaysOnEnabled() && mPocketGestureEnabled;
    }

    public boolean isIrSilencerEnabled() {
        return mIrSilencerEnabled;
    }

    public boolean isFlipToMuteEnabled() {
        return mFlipToMuteEnabled;
    }

    public boolean isLiftToSilenceEnabled() {
        return mLiftToSilenceEnabled;
    }

    public void chopChopAction() {
        new TorchAction(mContext).action();
    }

    private void loadPreferences(SharedPreferences sharedPreferences) {
        mChopChopEnabled = sharedPreferences.getBoolean(GESTURE_CHOP_CHOP_KEY, true);
        mIrWakeUpEnabled = sharedPreferences.getBoolean(GESTURE_IR_WAKEUP_KEY, true);
        mPickUpGestureEnabled = sharedPreferences.getBoolean(GESTURE_PICK_UP_KEY, true);
        mPocketGestureEnabled = sharedPreferences.getBoolean(GESTURE_POCKET_KEY, true);
        mIrSilencerEnabled = sharedPreferences.getBoolean(GESTURE_IR_SILENCER_KEY, false);
        mFlipToMuteEnabled = sharedPreferences.getBoolean(GESTURE_FLIP_TO_MUTE_KEY, false);
        mLiftToSilenceEnabled = sharedPreferences.getBoolean(GESTURE_LIFT_TO_SILENCE_KEY, false);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
                    boolean updated = true;

                    if (GESTURE_CHOP_CHOP_KEY.equals(key)) {
                        mChopChopEnabled = sharedPrefs.getBoolean(GESTURE_CHOP_CHOP_KEY, true);
                    } else if (GESTURE_IR_WAKEUP_KEY.equals(key)) {
                        mIrWakeUpEnabled = sharedPrefs.getBoolean(GESTURE_IR_WAKEUP_KEY, true);
                    } else if (GESTURE_PICK_UP_KEY.equals(key)) {
                        mPickUpGestureEnabled = sharedPrefs.getBoolean(GESTURE_PICK_UP_KEY, true);
                    } else if (GESTURE_POCKET_KEY.equals(key)) {
                        mPocketGestureEnabled = sharedPrefs.getBoolean(GESTURE_POCKET_KEY, true);
                    } else if (GESTURE_IR_SILENCER_KEY.equals(key)) {
                        mIrSilencerEnabled = sharedPrefs.getBoolean(GESTURE_IR_SILENCER_KEY, false);
                    } else if (GESTURE_FLIP_TO_MUTE_KEY.equals(key)) {
                        mFlipToMuteEnabled = sharedPrefs.getBoolean(GESTURE_FLIP_TO_MUTE_KEY, false);
                    } else if (GESTURE_LIFT_TO_SILENCE_KEY.equals(key)) {
                        mLiftToSilenceEnabled = sharedPrefs.getBoolean(GESTURE_LIFT_TO_SILENCE_KEY, false);
                    } else {
                        updated = false;
                    }

                    if (updated) {
                        mUpdatedStateNotifier.updateState();
                    }
                }
            };
}
