<!--
     SPDX-FileCopyrightText: The LineageOS Project
     SPDX-License-Identifier: Apache-2.0
-->

# Motorola face unlock wrapper

Bridges AOSP's `android.hardware.biometrics.face` AIDL HAL (V5, Android 17 / lineage-24.0) to Motorola's
`com.motorola.faceunlock` APK (ArcSoft, v02.01.655). On stock Motorola ROMs that APK is bound directly by a
patched framework; on Lineage there is no such framework, so this wrapper provides the HAL.

Verified on rtwo (lineage-24.0) with SELinux enforcing: enrollment and unlock work.

```
FaceService (system_server)
   │  IFace / ISession / ISessionCallback          (AOSP AIDL)
android.hardware.biometrics.face-service.motorola  (C++ vendor daemon, aidl/face)
   │  com.motorola.hardware.biometric.face.bridge  (oneway AIDL, interfaces/motorola/aidl)
MotoFaceBridge                                     (Java system_ext priv-app, packages/MotoFaceBridge)
   │  bindService com.motorola.faceunlock.BIND     (IMotoFaceService, packages/moto-framework)
com.motorola.faceunlock .service.FaceAuthService   (Motorola APK: camera + ArcSoft matching)
```

A native HAL cannot `bindService()` an app service, and an app should not register a HAL name, hence the
daemon + bridge app split.

## Layout

| Path | Contents |
|---|---|
| `aidl/face/` | HAL daemon (`Face`, `Session`, `BridgeHost`), `.rc`, VINTF manifest |
| `interfaces/motorola/aidl/com/motorola/hardware/biometric/face/bridge/` | `IFaceBridge`, `IFaceBridgeCallback`, `IFaceBridgeHost` |
| `interfaces/motorola/aidl/aidl_api/.../face.bridge-aidl/current/` | checked-in API dump (`frozen: false`) |
| `packages/MotoFaceBridge/` | bridge app and its privapp permission allowlist |
| `packages/moto-framework/` | `moto-face-framework` library and permission xmls |
| `sepolicy/qti/vendor/` | `hal_face_moto.te`, face entries in `file_contexts` and `service_contexts` |
| `vintf/device_framework_matrix.xml` | optional `com.motorola.hardware.biometric.face.bridge` HAL entry |

## Design notes

* **Sensor:** advertised as `CONVENIENCE` (2D RGB, spoofable): lock screen only, empty HAT, authenticator id 0.
  The sensor id is 10 (`kFaceSensorId` in `aidl/face/Face.cpp`). It must differ from every fingerprint sensor id,
  otherwise `BiometricService` throws "Cannot register duplicate authenticator" and `system_server` crash-loops.
* **`moto-face-framework`:** Motorola's patched `framework.jar` defines `com.motorola.internal.app.IMotoFaceService`
  and `IMotoFaceServiceReceiver`; Lineage's does not, and the APK crashes in `FaceAuthService.onCreate`
  (`NoClassDefFoundError`). They are re-created here as AIDL with the stock transaction codes (fixed by method
  order). A `MotoDevicePolicyManager` stub is included because the APK casts to it and only catches
  `SecurityException`. `moto-core_services.xml` replaces the stock one and adds
  `dependency="moto-face-framework"`; do not also ship the stock file.
* **VINTF manifest:** installed as a plain `prebuilt_etc` copy. `vintf_fragment` / `vintf_fragments` run the file
  through `assemble_vintf`, which dropped the bridge entry. Because the bridge HAL is not in the framework
  compatibility matrix, it has to be added there as `optional="true"` (all Motorola devices share the matrix).
* **Enrollment UI:** Lineage's Settings preview cannot reach the APK (`IMotoFaceService.enroll` has no surface
  argument), so enrollment must use Motorola's own UI (see the overlay below).
* **Attention / eyes-open:** the APK stores the flag but does not appear to enforce it, so it is not supported.
* **Cancel:** the HAL contract requires `onError(CANCELED)`; the bridge synthesizes it if the APK stays silent.

## Opt-in (device tree)

```

# Face unlock
PRODUCT_PACKAGES += \
    android.hardware.biometrics.face-service.motorola

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.biometrics.face.xml

```

1. **Blobs** (`proprietary-files.txt`, from the stock dump):
   ```

   # Face Unlock
   product/etc/default-permissions/default-permissions-faceunlock.xml
   product/etc/permissions/com.motorola.faceunlock.xml
   product/etc/permissions/privapp-permissions-com.motorola.faceunlock.xml
   product/etc/sysconfig/hiddenapi-whitelist-com.motorola.faceunlock.xml
   product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libarcsoft-lib.so:product/lib64/libarcsoft-lib.so
   product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libarcsoft_faceid.so:product/lib64/libarcsoft_faceid.so
   product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libarcsoftbase.so:product/lib64/libarcsoftbase.so;FIX_SONAME
   product/priv-app/MotoFaceUnlockArcSoft/lib/arm64/libmpbase.so:product/lib64/libmpbase.so
   product/priv-app/MotoFaceUnlockArcSoft/MotoFaceUnlockArcSoft.apk
   system/etc/permissions/moto-settings.xml:system_ext/etc/permissions/moto-settings.xml
   system/framework/moto-core_services.jar:system_ext/framework/moto-core_services.jar
   system/framework/moto-settings.jar:system_ext/framework/moto-settings.jar
   system_ext/priv-app/MotorolaSettingsProvider/MotorolaSettingsProvider.apk
   vendor/lib64/libmpbase.so;MODULE_SUFFIX=_vendor

   ```
   * **No `;PRESIGNED` on the APK.** It must be re-signed with the platform key: it needs the signature
     permissions `USE_BIOMETRIC_INTERNAL` and `MANAGE_BIOMETRIC`, otherwise `FaceEnrollActivity` crashes with
     `SecurityException`.
   * All four ArcSoft libraries must sit together in the app's `lib/arm64` (`libarcsoft-lib.so` depends on the
     other three; a missing `libmpbase.so` gives `UnsatisfiedLinkError` in `ArcImpl.arcInit`).
   * `moto-settings.xml` still points at `/system/framework/`; fix it in `extract-files.py`:
     ```python
     'system_ext/etc/permissions/moto-settings.xml': blob_fixup()
         .regex_replace('/system/framework/', '/system_ext/framework/'),
     ```
   * Do not take the stock `moto-core_services.xml` (replaced by `packages/moto-framework`), and skip `oat/` files.
```
   Examples in the topic:
   https://review.lineageos.org/q/topic:%22moto-faceunlock%22
```
2. **Settings overlay** so enrollment launches Motorola's UI:
   ```xml
   <string name="config_face_enroll" translatable="false">com.motorola.faceunlock/com.motorola.faceunlock.SetupFaceIntroActivity</string>
   ```
   (a static `com.android.settings` RRO on the product partition).
3. **Optional:** hide the unsupported attention switch with a Settings-side boolean read by
   `FaceFeatureProviderImpl.isAttentionSupported()`, set to `false` in the overlay.
4. **sepolicy** directory `hardware/motorola/sepolicy/qti/vendor` in the device's `BOARD_VENDOR_SEPOLICY_DIRS`.

## sepolicy notes

* `hal_face_moto` (HAL daemon) and its labels are in `sepolicy/qti/vendor`. The bridge app uses
  `sharedUserId android.uid.system`, so it runs as `system_app`; the binder rules and
  `allow system_app hal_face_service:service_manager find;` apply to that domain.
* The Motorola APK ends up in `platform_app_36` (platform-signed). A dedicated domain was tried and dropped:
  vendor `seapp_contexts` may not label a platform app on `/product` (Treble labeling check), a public type needs
  compat mapping, and a core domain cannot be granted vendor files. The remaining denials for the APK
  (`/vendor/dsp` as `adsprpcd_file`, and a `vendor_mot_hw_prop` read) are tolerated and can be ignored.

## Troubleshooting

| Symptom | Cause |
|---|---|
| `system_server` crash loop, "Cannot register duplicate authenticator" | face sensor id equals a fingerprint sensor id |
| servicemanager "Could not find ...IFaceBridgeHost/default in the VINTF manifest" | manifest missing the bridge entry, or not in the framework matrix |
| `NoClassDefFoundError ...IMotoFaceService$Stub` | `moto-face-framework` / `moto-core_services.xml` not installed |
| `SecurityException ... USE_BIOMETRIC_INTERNAL` | APK still `PRESIGNED` |
| `UnsatisfiedLinkError ... arcInit`, dlopen "libmpbase.so not found" | ArcSoft libraries not all in the app's `lib/arm64` |
| blank circle during enrollment | Settings' own enrollment UI in use; apply the `config_face_enroll` overlay |

To inspect stock jars/APKs, a small dex dumper that lists class shapes, constants and method signatures was used
during bring-up (the interfaces above were derived from `framework.jar` and `moto-core_services.jar` that way).
