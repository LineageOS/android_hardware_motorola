# Motorola face unlock wrapper (hardware/motorola/face)

Bridges AOSP's `android.hardware.biometrics.face` AIDL HAL (V5, Android 17 / lineage-24.0)
to Motorola's `com.motorola.faceunlock` APK (ArcSoft, v02.01.655), which on stock is bound
by Motorola's patched framework instead of a HAL.

```
FaceService (system_server)
   │  IFace / ISession / ISessionCallback  (AOSP AIDL)
android.hardware.biometrics.face-service.motorola      (C++, vendor, service/)
   │  lineageos.motorola.face.bridge.*     (oneway AIDL, aidl/)
MotoFaceBridge                                         (Java, system_ext priv-app, app/)
   │  bindService com.motorola.faceunlock.BIND + MotoFaceService binder
com.motorola.faceunlock .service.FaceAuthService       (Motorola APK, owns camera + ArcSoft)
```

Why two processes: a native HAL cannot `bindService()` an app service, and an app should not
register a HAL service name.

## What was verified from the APK
* Service `com.motorola.faceunlock.service.FaceAuthService`, action `com.motorola.faceunlock.BIND`,
  guarded by `android.permission.MANAGE_BIOMETRIC`.
* Binder object comes from `motorola.core_services.face.MotoFaceService` (not in the APK; it is the
  `moto-core_services` shared library). Methods: `authenticate(long)`, `cancel()`, `enroll(byte[],int,int[])`,
  `enumerate():int`, `generateChallenge(int):long`, `getAuthenticatorId():int`, `getFeature(int,int):boolean`,
  `getFeatureCount():int`, `remove(int)`, `resetLockout(byte[])`, `revokeChallenge():int`,
  `setCallback(MotoFaceServiceReceiver)`, `setFeature(int,boolean,byte[],int)`.
* Callback `MotoFaceServiceReceiver`: `onAuthenticated(int,int,byte[])`, `onEnrollResult(int,int,int)`,
  `onEnumerate(int[],int)`, `onError(int,int)`, `onRemoved(int[],int)`. No acquired/lockout callbacks.
* Native lib: `System.loadLibrary("arcsoft-lib")`. The uploaded APK has **no `lib/` directory**, so
  `libarcsoft-lib.so` must be extracted from the device (app lib dir) and shipped next to the APK.
* APK minSdk 36, `uses-library moto-core_services` and `moto-settings`.
* The APK ships its own enrollment UI (`SetupFaceIntroActivity`, exported) which calls
  `FaceManager.enroll()` through `motorola.wrap.android.hardware.face.FaceManager_wrap`.

## Verify first (guesses; I only had the APK, not the framework jar or a build tree)
1. **Moto framework classes.** Stock `framework.jar` defines `com.motorola.internal.app.IMotoFaceService` and
   `IMotoFaceServiceReceiver`; Lineage does not, so the APK crashed in `FaceAuthService.onCreate`
   (`NoClassDefFoundError ...IMotoFaceService$Stub`). `moto-framework/` re-creates them from a dump of the stock
   jar (`tools/dumpclasses.py`; transaction codes are fixed by method order, see the AIDL comments) and ships them as
   the shared library `moto-face-framework`. `moto-core_services.xml` here replaces the stock one and adds
   `dependency="moto-face-framework"` so the APK's class loader can see them. Diff it against the stock xml.
2. **AIDL versions** in `service/Android.bp` (face V5, common V4, keymaster V4, hardware.common V2).
   If ISession in your tree also has a pure-virtual `enrollWithOptions(...)`, add it to `Session` and
   forward to `enroll()`. Fix any other signature the compiler reports.
3. **AIDL API dump.** First build needs `m lineageos.motorola.face.bridge-aidl-update-api` and the generated
   `aidl_api/` directory committed alongside `Android.bp`.
   **`aidl_interface` freezing** for `lineageos.motorola.face.bridge` (`frozen: false` is set). If your tree
   insists, run `m lineageos.motorola.face.bridge-aidl-freeze-api`.
4. **Moto error numbers** (`MOTO_*` in `Session.cpp`) assume `FaceManager.FACE_ERROR_*`.
5. **sepolicy**: expect to iterate on denials (`system_app` finding `hal_face_service`, camera, ArcSoft
   data dirs, `neverallow` complaints). Giving the bridge its own domain via `seapp_contexts` is cleaner.
6. **Camera access**: the APK opens Camera1 from the background. The bridge binds with `BIND_IMPORTANT`
   to keep the uid active; the APK needs `CAMERA` pre-granted (default-permissions) and privapp whitelisting.
7. **Settings**: Lineage Settings must launch Motorola's enrollment activity, e.g. overlay `config_face_enroll`
   to `com.motorola.faceunlock/.SetupFaceIntroActivity` (check the key name in your Settings tree).

## Sensor id
The face sensor reports id 10 (`kFaceSensorId` in `service/Face.cpp`). It must differ from every fingerprint
sensor id or system_server crash-loops with "Cannot register duplicate authenticator". Check the ids in use with
`adb shell dumpsys biometric`.

## Security notes
* Sensor is advertised as `CONVENIENCE` (2D RGB, spoofable): keyguard only, empty HAT, authenticatorId 0.
* The HAT given to `enroll` is serialized as a 69-byte `hw_auth_token_t` (`HatUtils.h`); Motorola's APK is
  not known to verify it.

## Opt-in
In the device tree:
```
PRODUCT_PACKAGES += lineageos.motorola.face.bridge
```
`lineageos.motorola.face.bridge` is a `phony` module that installs the HAL daemon
(`android.hardware.biometrics.face-service.motorola`) and `MotoFaceBridge`. It replaces any existing face HAL.

Also required:
* **sepolicy** (extract the zip into `hardware/motorola/`; everything is in `sepolicy/qti/vendor/`):
  * `hal_face_moto.te`, `file_contexts`, `service_contexts`: face HAL domain and labels (`# Face` sections
    already merged in).
  * `motorola_faceunlock.te`: the app domain for `com.motorola.faceunlock`, defined entirely in vendor policy
    (a public type would need compat mapping entries in system/sepolicy, and `app_domain()` cannot be in public
    policy), plus the `adsprpcd_file` rule.
  * `seapp_contexts`: labels the app with that domain. If your repo already has this file, append the single
    line instead of overwriting it.
  Do not keep `qti/public/motorola_faceunlock.te` or a `qti/private` copy of the domain, or the type is defined
  twice. After a label change wipe the app's data (or flash with a data wipe); check with
  `adb shell ps -AZ | grep faceunlock`.
* **Blobs** (from the stock rtwo dump) for `proprietary-files.txt`:
```
# Motorola face unlock
product/priv-app/MotoFaceUnlockArcSoft/MotoFaceUnlockArcSoft.apk;PRESIGNED;PRIVILEGED
product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libarcsoftbase.so
product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libarcsoft_faceid.so
product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libarcsoft-lib.so
product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libmpbase.so
product/etc/permissions/com.motorola.faceunlock.xml
product/etc/permissions/privapp-permissions-com.motorola.faceunlock.xml
product/etc/default-permissions/default-permissions-faceunlock.xml
product/etc/sysconfig/hiddenapi-whitelist-com.motorola.faceunlock.xml
product/etc/init/com.motorola.faceunlock.rc
system/framework/moto-core_services.jar:system_ext/framework/moto-core_services.jar
system/framework/moto-settings.jar:system_ext/framework/moto-settings.jar
system/etc/permissions/moto-settings.xml:system_ext/etc/permissions/moto-settings.xml
```
  Examples in the topic:
  https://review.lineageos.org/q/topic:%22moto-faceunlock%22
```
  The stock `moto-settings.xml` still points at `/system/framework/moto-settings.jar`, so fix it in
  `extract-files.py` (check the file first with `grep file= moto-settings.xml`):
```python
    'system_ext/etc/permissions/moto-settings.xml': blob_fixup()
        .regex_replace('/system/framework/', '/system_ext/framework/'),
```
  Do **not** take the stock `system/etc/permissions/moto-core_services.xml` (this repo installs a replacement
  to `/system_ext/etc/permissions/` and points at `/system_ext/framework/moto-core_services.jar`); two definitions of the same library conflict. Skip `oat/` files.
  Optional: `moto-enterprise-internal.jar` + xmls (the APK calls `MotoDevicePolicyManager`).

Status: **not compiled or run** (no AOSP tree available while writing).
