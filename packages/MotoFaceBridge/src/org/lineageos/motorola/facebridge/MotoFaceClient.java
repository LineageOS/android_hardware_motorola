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

    @Override public void setEvents(Events events) { mEvents = events; }

    /** Safe to call repeatedly; (re)binds if needed. */
    void ensureBound() {
        synchronized (mLock) {
            if (mRemote != null || mBinding) return;
            Intent i = new Intent(ACTION).setComponent(new ComponentName(PKG, SERVICE));
            try {
                mBinding = mContext.bindServiceAsUser(i, mConn,
                        Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.SYSTEM);
                if (!mBinding) Log.e(TAG, "bindService returned false (APK missing / user locked?)");
            } catch (RuntimeException e) {
                Log.e(TAG, "bindService failed", e);
            }
        }
    }

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IMotoFaceService svc = IMotoFaceService.Stub.asInterface(binder);
            try {
                svc.setCallback(mReceiver);
                synchronized (mLock) { mRemote = svc; }
                Log.i(TAG, "connected to " + name.flattenToShortString());
            } catch (RemoteException | RuntimeException e) {
                Log.e(TAG, "setCallback failed", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "Moto service disconnected");
            synchronized (mLock) { mRemote = null; }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            synchronized (mLock) { mRemote = null; mBinding = false; }
            ensureBound();
        }
    };

    private final IMotoFaceServiceReceiver.Stub mReceiver = new IMotoFaceServiceReceiver.Stub() {
        @Override public void onEnrollResult(int faceId, int userId, int remaining) {
            Events e = mEvents; if (e != null) e.onEnrollResult(faceId, userId, remaining);
        }
        @Override public void onAuthenticated(int faceId, int userId, byte[] token) {
            Events e = mEvents; if (e != null) e.onAuthenticated(faceId, userId, token);
        }
        @Override public void onAcquired(int a, int b, int c) { /* not forwarded */ }
        @Override public void onError(int error, int vendorCode) {
            Events e = mEvents; if (e != null) e.onError(error, vendorCode);
        }
        @Override public void onRemoved(int[] faceIds, int userId) {
            Events e = mEvents; if (e != null) e.onRemoved(faceIds, userId);
        }
        @Override public void onEnumerate(int[] faceIds, int userId) {
            Events e = mEvents; if (e != null) e.onEnumerate(faceIds, userId);
        }
        @Override public void onLockoutChanged(long durationMs) { /* APK also sends onError(LOCKOUT) */ }
    };

    private IMotoFaceService svc() {
        ensureBound();
        synchronized (mLock) {
            if (mRemote == null) throw new IllegalStateException("Moto service not connected");
            return mRemote;
        }
    }

    @Override public long generateChallenge(int t) throws Exception { return svc().generateChallenge(t); }
    @Override public int revokeChallenge() throws Exception { return svc().revokeChallenge(); }
    @Override public void enroll(byte[] hat, int t, int[] d) throws Exception { svc().enroll(hat, t, d); }
    @Override public void authenticate(long op) throws Exception { svc().authenticate(op); }
    @Override public void cancel() throws Exception { svc().cancel(); }
    @Override public int enumerate() throws Exception { return svc().enumerate(); }
    @Override public void remove(int id) throws Exception { svc().remove(id); }
    @Override public void resetLockout(byte[] hat) throws Exception { svc().resetLockout(hat); }
    @Override public boolean getFeature(int f, int id) throws Exception { return svc().getFeature(f, id); }
    @Override public void setFeature(int f, boolean en, byte[] hat, int id) throws Exception { svc().setFeature(f, en, hat, id); }
}
