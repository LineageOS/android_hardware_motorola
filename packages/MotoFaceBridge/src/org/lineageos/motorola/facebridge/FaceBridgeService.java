/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
 
package org.lineageos.motorola.facebridge;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.motorola.hardware.biometric.face.bridge.IFaceBridge;
import com.motorola.hardware.biometric.face.bridge.IFaceBridgeCallback;
import com.motorola.hardware.biometric.face.bridge.IFaceBridgeHost;

/** Registers with the HAL daemon and relays its requests to Motorola's FaceAuthService. */
public class FaceBridgeService extends Service implements MotoBackend.Events {
    private static final String TAG = "MotoFaceBridge";
    private static final String HOST = IFaceBridgeHost.DESCRIPTOR + "/default";
    private static final int FACE_ERROR_HW_UNAVAILABLE = 1;
    private static final int FACE_ERROR_UNABLE_TO_PROCESS = 2;
    private static final int FACE_ERROR_CANCELED = 5;
    private static final long CANCEL_GRACE_MS = 700;

    private HandlerThread mThread;
    private Handler mHandler;
    private MotoFaceClient mMoto;
    private volatile IFaceBridgeCallback mCb;
    private volatile boolean mOpActive;   // enroll/authenticate in flight

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread("moto-face-bridge");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mMoto = new MotoFaceClient(this);
        mMoto.setEvents(this);
        // The APK is not directBootAware: bind once the user is unlocked.
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { mHandler.post(mMoto::ensureBound); }
        }, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        mHandler.post(mMoto::ensureBound);
        new Thread(this::registerWithHost, "moto-face-register").start();
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void registerWithHost() {
        while (true) {
            try {
                IBinder b = ServiceManager.waitForDeclaredService(HOST);
                if (b == null) { Thread.sleep(2000); continue; }
                IFaceBridgeHost host = IFaceBridgeHost.Stub.asInterface(b);
                host.registerBridge(mBridge);
                Log.i(TAG, "registered with HAL");
                final Object wait = new Object();
                b.linkToDeath(() -> { synchronized (wait) { wait.notifyAll(); } }, 0);
                synchronized (wait) { wait.wait(); }
                Log.w(TAG, "HAL died, re-registering");
            } catch (Exception e) {
                Log.e(TAG, "register failed", e);
                try { Thread.sleep(2000); } catch (InterruptedException ie) { return; }
            }
        }
    }

    // ------------------------------------------------------------ HAL -> Moto

    private interface Op { void run() throws Exception; }

    private void post(Op op) {
        mHandler.post(() -> {
            try {
                op.run();
            } catch (Exception e) {
                Log.e(TAG, "Moto call failed", e);
                mOpActive = false;
                send(cb -> cb.onError(FACE_ERROR_HW_UNAVAILABLE, 0));
            }
        });
    }

    private interface CbOp { void run(IFaceBridgeCallback cb) throws RemoteException; }

    private void send(CbOp op) {
        IFaceBridgeCallback cb = mCb;
        if (cb == null) return;
        try { op.run(cb); } catch (RemoteException e) { Log.e(TAG, "HAL callback failed", e); }
    }

    private final IFaceBridge.Stub mBridge = new IFaceBridge.Stub() {
        // Required for versioned (stable) AIDL Java stubs.
        @Override public int getInterfaceVersion() { return IFaceBridge.VERSION; }
        @Override public String getInterfaceHash() { return IFaceBridge.HASH; }

        @Override public void setCallback(IFaceBridgeCallback cb) { mCb = cb; }

        @Override public void generateChallenge(int timeoutSec) {
            post(() -> { long c = mMoto.generateChallenge(timeoutSec); send(cb -> cb.onChallengeGenerated(c)); });
        }
        @Override public void revokeChallenge() {
            post(() -> { mMoto.revokeChallenge(); send(cb -> cb.onChallengeRevoked()); });
        }
        @Override public void enroll(byte[] hat, int timeoutSec, int[] disabled) {
            post(() -> { mOpActive = true; mMoto.enroll(hat, timeoutSec, disabled); });
        }
        @Override public void authenticate(long operationId) {
            post(() -> { mOpActive = true; mMoto.authenticate(operationId); });
        }
        @Override public void cancel() {
            // Own thread: must not queue behind a Moto call that is still blocking.
            new Thread(() -> {
                try { mMoto.cancel(); } catch (Exception e) { Log.e(TAG, "cancel failed", e); }
                // The HAL contract requires onError(CANCELED); synthesize it if the APK stays silent.
                mHandler.postDelayed(() -> {
                    if (mOpActive) {
                        mOpActive = false;
                        send(cb -> cb.onError(FACE_ERROR_CANCELED, 0));
                    }
                }, CANCEL_GRACE_MS);
            }, "moto-face-cancel").start();
        }
        @Override public void enumerate() {
            post(() -> {
                if (mMoto.enumerate() != 0) send(cb -> cb.onError(FACE_ERROR_UNABLE_TO_PROCESS, 0));
            });
        }
        @Override public void remove(int faceId) { post(() -> mMoto.remove(faceId)); }
        @Override public void resetLockout(byte[] hat) {
            post(() -> { mMoto.resetLockout(hat); send(cb -> cb.onLockoutCleared()); });
        }
        @Override public void getFeature(int feature, int faceId) {
            post(() -> { boolean on = mMoto.getFeature(feature, faceId); send(cb -> cb.onFeature(feature, on)); });
        }
        @Override public void setFeature(int feature, boolean enabled, byte[] hat, int faceId) {
            post(() -> { mMoto.setFeature(feature, enabled, hat, faceId); send(cb -> cb.onFeature(feature, enabled)); });
        }
    };

    // ------------------------------------------------------------ Moto -> HAL

    @Override public void onEnrollResult(int faceId, int userId, int remaining) {
        if (remaining == 0) mOpActive = false;
        send(cb -> cb.onEnrollResult(faceId, userId, remaining));
    }
    @Override public void onAuthenticated(int faceId, int userId, byte[] token) {
        mOpActive = false;
        send(cb -> cb.onAuthenticated(faceId, userId));
    }
    @Override public void onError(int error, int vendorCode) {
        mOpActive = false;
        send(cb -> cb.onError(error, vendorCode));
    }
    @Override public void onRemoved(int[] ids, int userId) { send(cb -> cb.onRemoved(ids, userId)); }
    @Override public void onEnumerate(int[] ids, int userId) { send(cb -> cb.onEnumerate(ids, userId)); }
}
