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
    private static final long REGISTER_RETRY_MS = 2000;

    private HandlerThread mThread;
    private Handler mHandler;
    private MotoFaceClient mMoto;
    private volatile IFaceBridgeCallback mCallback;
    // True while an enroll or authenticate is in flight.
    private volatile boolean mOpActive;

    private final BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.post(mMoto::ensureBound);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread("moto-face-bridge");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mMoto = new MotoFaceClient(this);
        mMoto.setEvents(this);

        // The APK is not directBootAware: bind once the user is unlocked.
        registerReceiver(mUserUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        mHandler.post(mMoto::ensureBound);

        new Thread(this::registerWithHost, "moto-face-register").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerWithHost() {
        while (true) {
            try {
                IBinder binder = ServiceManager.waitForDeclaredService(HOST);
                if (binder == null) {
                    Thread.sleep(REGISTER_RETRY_MS);
                    continue;
                }
                IFaceBridgeHost host = IFaceBridgeHost.Stub.asInterface(binder);
                host.registerBridge(mBridge);
                Log.i(TAG, "registered with HAL");

                final Object deathLock = new Object();
                binder.linkToDeath(() -> {
                    synchronized (deathLock) {
                        deathLock.notifyAll();
                    }
                }, 0);
                synchronized (deathLock) {
                    deathLock.wait();
                }
                Log.w(TAG, "HAL died, re-registering");
            } catch (Exception e) {
                Log.e(TAG, "register failed", e);
                try {
                    Thread.sleep(REGISTER_RETRY_MS);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    // HAL -> Moto

    private interface MotoOp {
        void run() throws Exception;
    }

    private void post(MotoOp op) {
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

    private interface CallbackOp {
        void run(IFaceBridgeCallback cb) throws RemoteException;
    }

    private void send(CallbackOp op) {
        IFaceBridgeCallback cb = mCallback;
        if (cb == null) {
            return;
        }
        try {
            op.run(cb);
        } catch (RemoteException e) {
            Log.e(TAG, "HAL callback failed", e);
        }
    }

    private final IFaceBridge.Stub mBridge = new IFaceBridge.Stub() {
        // Required for versioned (stable) AIDL Java stubs.
        @Override
        public int getInterfaceVersion() {
            return IFaceBridge.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IFaceBridge.HASH;
        }

        @Override
        public void setCallback(IFaceBridgeCallback cb) {
            mCallback = cb;
        }

        @Override
        public void generateChallenge(int timeoutSec) {
            post(() -> {
                long challenge = mMoto.generateChallenge(timeoutSec);
                send(cb -> cb.onChallengeGenerated(challenge));
            });
        }

        @Override
        public void revokeChallenge() {
            post(() -> {
                mMoto.revokeChallenge();
                send(cb -> cb.onChallengeRevoked());
            });
        }

        @Override
        public void enroll(byte[] hat, int timeoutSec, int[] disabledFeatures) {
            post(() -> {
                mOpActive = true;
                mMoto.enroll(hat, timeoutSec, disabledFeatures);
            });
        }

        @Override
        public void authenticate(long operationId) {
            post(() -> {
                mOpActive = true;
                mMoto.authenticate(operationId);
            });
        }

        @Override
        public void cancel() {
            // Own thread: must not queue behind a Moto call that is still blocking.
            new Thread(() -> {
                try {
                    mMoto.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "cancel failed", e);
                }
                // The HAL contract requires onError(CANCELED); synthesize it if the APK
                // stays silent.
                mHandler.postDelayed(() -> {
                    if (mOpActive) {
                        mOpActive = false;
                        send(cb -> cb.onError(FACE_ERROR_CANCELED, 0));
                    }
                }, CANCEL_GRACE_MS);
            }, "moto-face-cancel").start();
        }

        @Override
        public void enumerate() {
            post(() -> {
                if (mMoto.enumerate() != 0) {
                    send(cb -> cb.onError(FACE_ERROR_UNABLE_TO_PROCESS, 0));
                }
            });
        }

        @Override
        public void remove(int faceId) {
            post(() -> mMoto.remove(faceId));
        }

        @Override
        public void resetLockout(byte[] hat) {
            post(() -> {
                mMoto.resetLockout(hat);
                send(cb -> cb.onLockoutCleared());
            });
        }

        @Override
        public void getFeature(int feature, int faceId) {
            post(() -> {
                boolean enabled = mMoto.getFeature(feature, faceId);
                send(cb -> cb.onFeature(feature, enabled));
            });
        }

        @Override
        public void setFeature(int feature, boolean enabled, byte[] hat, int faceId) {
            post(() -> {
                mMoto.setFeature(feature, enabled, hat, faceId);
                send(cb -> cb.onFeature(feature, enabled));
            });
        }
    };

    // Moto -> HAL

    @Override
    public void onEnrollResult(int faceId, int userId, int remaining) {
        if (remaining == 0) {
            mOpActive = false;
        }
        send(cb -> cb.onEnrollResult(faceId, userId, remaining));
    }

    @Override
    public void onAuthenticated(int faceId, int userId, byte[] token) {
        mOpActive = false;
        send(cb -> cb.onAuthenticated(faceId, userId));
    }

    @Override
    public void onError(int error, int vendorCode) {
        mOpActive = false;
        send(cb -> cb.onError(error, vendorCode));
    }

    @Override
    public void onRemoved(int[] faceIds, int userId) {
        send(cb -> cb.onRemoved(faceIds, userId));
    }

    @Override
    public void onEnumerate(int[] faceIds, int userId) {
        send(cb -> cb.onEnumerate(faceIds, userId));
    }
}
