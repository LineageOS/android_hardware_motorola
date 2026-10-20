/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.motorola.facebridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.motorola.internal.app.IMotoFaceService;
import com.motorola.internal.app.IMotoFaceServiceReceiver;

/** Binds com.motorola.faceunlock's FaceAuthService and speaks IMotoFaceService to it. */
class MotoFaceClient implements MotoBackend {
    private static final String TAG = "MotoFaceClient";

    static final String PKG = "com.motorola.faceunlock";
    static final String SERVICE = "com.motorola.faceunlock.service.FaceAuthService";
    static final String ACTION = "com.motorola.faceunlock.BIND";

    private final Context mContext;
    private final Object mLock = new Object();
    private volatile Events mEvents;
    private IMotoFaceService mRemote;
    private boolean mBinding;

    MotoFaceClient(Context context) {
        mContext = context;
    }

    @Override
    public void setEvents(Events events) {
        mEvents = events;
    }

    /** Safe to call repeatedly; (re)binds if needed. */
    void ensureBound() {
        synchronized (mLock) {
            if (mRemote != null || mBinding) {
                return;
            }
            Intent intent = new Intent(ACTION).setComponent(new ComponentName(PKG, SERVICE));
            try {
                mBinding = mContext.bindServiceAsUser(intent, mConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.SYSTEM);
                if (!mBinding) {
                    Log.e(TAG, "bindService returned false (APK missing / user locked?)");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "bindService failed", e);
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IMotoFaceService service = IMotoFaceService.Stub.asInterface(binder);
            try {
                service.setCallback(mReceiver);
                synchronized (mLock) {
                    mRemote = service;
                }
                Log.i(TAG, "connected to " + name.flattenToShortString());
            } catch (RemoteException | RuntimeException e) {
                Log.e(TAG, "setCallback failed", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Moto service disconnected");
            synchronized (mLock) {
                mRemote = null;
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            synchronized (mLock) {
                mRemote = null;
                mBinding = false;
            }
            ensureBound();
        }
    };

    private final IMotoFaceServiceReceiver.Stub mReceiver = new IMotoFaceServiceReceiver.Stub() {
        @Override
        public void onEnrollResult(int faceId, int userId, int remaining) {
            Events events = mEvents;
            if (events != null) {
                events.onEnrollResult(faceId, userId, remaining);
            }
        }

        @Override
        public void onAuthenticated(int faceId, int userId, byte[] token) {
            Events events = mEvents;
            if (events != null) {
                events.onAuthenticated(faceId, userId, token);
            }
        }

        @Override
        public void onAcquired(int a, int b, int c) {
            // Not forwarded.
        }

        @Override
        public void onError(int error, int vendorCode) {
            Events events = mEvents;
            if (events != null) {
                events.onError(error, vendorCode);
            }
        }

        @Override
        public void onRemoved(int[] faceIds, int userId) {
            Events events = mEvents;
            if (events != null) {
                events.onRemoved(faceIds, userId);
            }
        }

        @Override
        public void onEnumerate(int[] faceIds, int userId) {
            Events events = mEvents;
            if (events != null) {
                events.onEnumerate(faceIds, userId);
            }
        }

        @Override
        public void onLockoutChanged(long durationMs) {
            // The APK also sends onError(LOCKOUT).
        }
    };

    private IMotoFaceService service() {
        ensureBound();
        synchronized (mLock) {
            if (mRemote == null) {
                throw new IllegalStateException("Moto service not connected");
            }
            return mRemote;
        }
    }

    @Override
    public long generateChallenge(int timeoutSec) throws Exception {
        return service().generateChallenge(timeoutSec);
    }

    @Override
    public int revokeChallenge() throws Exception {
        return service().revokeChallenge();
    }

    @Override
    public void enroll(byte[] hat, int timeoutSec, int[] disabledFeatures) throws Exception {
        service().enroll(hat, timeoutSec, disabledFeatures);
    }

    @Override
    public void authenticate(long operationId) throws Exception {
        service().authenticate(operationId);
    }

    @Override
    public void cancel() throws Exception {
        service().cancel();
    }

    @Override
    public int enumerate() throws Exception {
        return service().enumerate();
    }

    @Override
    public void remove(int faceId) throws Exception {
        service().remove(faceId);
    }

    @Override
    public void resetLockout(byte[] hat) throws Exception {
        service().resetLockout(hat);
    }

    @Override
    public boolean getFeature(int feature, int faceId) throws Exception {
        return service().getFeature(feature, faceId);
    }

    @Override
    public void setFeature(int feature, boolean enabled, byte[] hat, int faceId)
            throws Exception {
        service().setFeature(feature, enabled, hat, faceId);
    }
}
