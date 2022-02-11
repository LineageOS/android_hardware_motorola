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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import org.lineageos.settings.device.actions.CameraActivationSensor;
import org.lineageos.settings.device.actions.ChopChopSensor;
import org.lineageos.settings.device.actions.FlipToMute;
import org.lineageos.settings.device.actions.LiftToSilence;
import org.lineageos.settings.device.actions.ProximitySilencer;
import org.lineageos.settings.device.actions.UpdatedStateNotifier;
import org.lineageos.settings.device.doze.DozePulseAction;
import org.lineageos.settings.device.doze.FlatUpSensor;
import org.lineageos.settings.device.doze.ScreenReceiver;
import org.lineageos.settings.device.doze.ScreenStateNotifier;
import org.lineageos.settings.device.doze.StowSensor;

import java.util.LinkedList;
import java.util.List;

public class MotoActionsService extends IntentService
        implements ScreenStateNotifier, UpdatedStateNotifier {
    private static final String TAG = "MotoActions";

    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;

    private final List<ScreenStateNotifier> mScreenStateNotifiers = new LinkedList<>();
    private final List<UpdatedStateNotifier> mUpdatedStateNotifiers = new LinkedList<>();

    public MotoActionsService(Context context) {
        super("MotoActionservice");

        Log.d(TAG, "Starting");

        MotoActionsSettings actionsSettings = new MotoActionsSettings(context, this);
        SensorHelper sensorHelper = new SensorHelper(context);
        ScreenReceiver screenReceiver = new ScreenReceiver(context, this);
        DozePulseAction dozePulseAction = new DozePulseAction(context);

        mScreenStateNotifiers.add(dozePulseAction);

        // Actionable sensors get screen on/off notifications
        mScreenStateNotifiers.add(new StowSensor(actionsSettings, sensorHelper, dozePulseAction));
        mScreenStateNotifiers.add(new FlatUpSensor(actionsSettings, sensorHelper, dozePulseAction));

        // Other actions that are always enabled
        mUpdatedStateNotifiers.add(new CameraActivationSensor(actionsSettings, sensorHelper));
        mUpdatedStateNotifiers.add(new ChopChopSensor(actionsSettings, sensorHelper));
        mUpdatedStateNotifiers.add(new ProximitySilencer(actionsSettings, context, sensorHelper));
        mUpdatedStateNotifiers.add(new FlipToMute(actionsSettings, context, sensorHelper));
        mUpdatedStateNotifiers.add(new LiftToSilence(actionsSettings, context, sensorHelper));

        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock =
                mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MotoActionsWakeLock");
        updateState();
    }

    @Override
    protected void onHandleIntent(Intent intent) {}

    @Override
    public void screenTurnedOn() {
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
        }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOn();
        }
    }

    @Override
    public void screenTurnedOff() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        for (ScreenStateNotifier screenStateNotifier : mScreenStateNotifiers) {
            screenStateNotifier.screenTurnedOff();
        }
    }

    public void updateState() {
        if (mPowerManager.isInteractive()) {
            screenTurnedOn();
        } else {
            screenTurnedOff();
        }
        for (UpdatedStateNotifier notifier : mUpdatedStateNotifiers) {
            notifier.updateState();
        }
    }
}
