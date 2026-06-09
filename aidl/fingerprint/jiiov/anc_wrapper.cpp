// SPDX-License-Identifier: Apache-2.0
#include "anc_wrapper.h"

#include <dlfcn.h>
#include <log/log.h>
#include <string.h>

#include "Fingerprint.h"

namespace jiiov {

namespace {

constexpr const char* kAncLib = "anc.hal.so";

// Jiiov's own notification callback surface. Bears no relation to
// the legacy Android fingerprint_notify_t/fingerprint_msg_t format;
// see the anc_On* functions below for the translation.
struct JiiovNotify {
    void (*OnChallengeGenerated)(void* instance, uint64_t challenge);
    void (*OnChallengeRevoked)(void* instance, uint64_t challenge);
    void (*OnAuthenticatorIdRetrieved)(void* instance, uint64_t id);
    void (*OnAuthenticatorIdInvalidated)(void* instance, uint64_t id);
    void (*OnEnrollResult)(void* instance, uint32_t finger_id, uint32_t group_id,
                           uint32_t remaining);
    void (*OnAcquired)(void* instance, uint32_t acquiredInfo);
    void (*OnAuthenticated)(void* instance, uint32_t finger_id, uint32_t group_id,
                            const hw_auth_token_t* token, size_t len);
    void (*OnLockoutTimed)(void* instance, uint32_t duration);
    void (*OnLockoutPermanent)(void* instance);
    void (*OnError)(void* instance, uint32_t error, uint32_t vendorCode);
    void (*OnRemoved)(void* instance, uint32_t finger_id, uint32_t group_id, uint32_t remaining);
    void (*OnEnumerate)(void* instance, uint32_t finger_id, uint32_t group_id, uint32_t remaining);
};

// State cached once, at wrapper construction time, instead of being
// re-resolved (and anc.hal.so being re-dlopen'd) on every call.
void* g_anc_handle = nullptr;
void* g_anc_dev = nullptr;
fingerprint_notify_t g_legacy_notify = nullptr;

using AncSimpleFn = int (*)(void*);
using AncU64Fn = uint64_t (*)(void*);

AncSimpleFn g_anc_post_enroll = nullptr;
AncSimpleFn g_anc_cancel = nullptr;
AncSimpleFn g_anc_enumerate = nullptr;
AncU64Fn g_anc_pre_enroll = nullptr;
AncU64Fn g_anc_get_authenticator_id = nullptr;
int (*g_anc_enroll)(void*, const uint8_t*, uint32_t, uint32_t) = nullptr;
int (*g_anc_remove)(void*, uint32_t, uint32_t) = nullptr;
int (*g_anc_set_active_group)(void*, uint32_t, const char*) = nullptr;
int (*g_anc_authenticate)(void*, uint64_t) = nullptr;

// --- Notify translation: JiiovNotify -> fingerprint_msg_t ---

void AncOnEnrollResult(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_ENROLLING;
    msg.data.enroll.finger.fid = finger_id;
    msg.data.enroll.finger.gid = group_id;
    msg.data.enroll.samples_remaining = remaining;
    g_legacy_notify(&msg);
}

void AncOnAcquired(void*, uint32_t acquiredInfo) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_ACQUIRED;
    msg.data.acquired.acquired_info = (fingerprint_acquired_info_t)acquiredInfo;
    g_legacy_notify(&msg);
}

void AncOnAuthenticated(void*, uint32_t finger_id, uint32_t group_id, const hw_auth_token_t* token,
                        size_t /*len*/) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_AUTHENTICATED;
    msg.data.authenticated.finger.fid = finger_id;
    msg.data.authenticated.finger.gid = group_id;
    if (token) {
        msg.data.authenticated.hat = *token;
    }
    g_legacy_notify(&msg);
}

void AncOnError(void*, uint32_t error, uint32_t /*vendorCode*/) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_ERROR;
    msg.data.error = (fingerprint_error_t)error;
    // vendorCode is dropped: the legacy Moto HAL error type is a plain enum
    // with no room for a vendor-specific sub-code.
    g_legacy_notify(&msg);
}

void AncOnRemoved(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_REMOVED;
    msg.data.removed.finger.fid = finger_id;
    msg.data.removed.finger.gid = group_id;
    msg.data.removed.remaining_templates = remaining;
    g_legacy_notify(&msg);
}

void AncOnEnumerate(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_ENUMERATING;
    msg.data.enumerated.finger.fid = finger_id;
    msg.data.enumerated.finger.gid = group_id;
    msg.data.enumerated.remaining_templates = remaining;
    g_legacy_notify(&msg);
}

// Callbacks we don't translate: no legacy equivalent is consumed anywhere
// in this HAL, so they're no-ops rather than dead translation code.
void AncIgnore(void*, ...) {}
void AncIgnoreVoid(void*) {}

JiiovNotify g_jiiov_notify = {
        .OnChallengeGenerated = (void (*)(void*, uint64_t))AncIgnore,
        .OnChallengeRevoked = (void (*)(void*, uint64_t))AncIgnore,
        .OnAuthenticatorIdRetrieved = (void (*)(void*, uint64_t))AncIgnore,
        .OnAuthenticatorIdInvalidated = (void (*)(void*, uint64_t))AncIgnore,
        .OnEnrollResult = AncOnEnrollResult,
        .OnAcquired = AncOnAcquired,
        .OnAuthenticated = AncOnAuthenticated,
        .OnLockoutTimed = (void (*)(void*, uint32_t))AncIgnore,
        .OnLockoutPermanent = AncIgnoreVoid,
        .OnError = AncOnError,
        .OnRemoved = AncOnRemoved,
        .OnEnumerate = AncOnEnumerate,
};

// --- Legacy fingerprint_device_t entry points ---
// These call into anc.hal.so via the function pointers cached in
// CreateAncWrapper(), rather than re-dlopen()'ing the library each time.

int WrapperSetNotify(struct fingerprint_device*, fingerprint_notify_t notify) {
    g_legacy_notify = notify;
    return 0;
}

uint64_t WrapperPreEnroll(struct fingerprint_device*) {
    return g_anc_pre_enroll ? g_anc_pre_enroll(g_anc_dev) : 0;
}

int WrapperEnroll(struct fingerprint_device*, const hw_auth_token_t* hat, uint32_t gid,
                  uint32_t timeout_sec) {
    return g_anc_enroll ? g_anc_enroll(g_anc_dev, (const uint8_t*)hat, gid, timeout_sec) : -1;
}

int WrapperPostEnroll(struct fingerprint_device*) {
    return g_anc_post_enroll ? g_anc_post_enroll(g_anc_dev) : -1;
}

uint64_t WrapperGetAuthenticatorId(struct fingerprint_device*) {
    return g_anc_get_authenticator_id ? g_anc_get_authenticator_id(g_anc_dev) : 0;
}

int WrapperCancel(struct fingerprint_device*) {
    return g_anc_cancel ? g_anc_cancel(g_anc_dev) : -1;
}

int WrapperEnumerate(struct fingerprint_device*) {
    return g_anc_enumerate ? g_anc_enumerate(g_anc_dev) : -1;
}

int WrapperRemove(struct fingerprint_device*, uint32_t gid, uint32_t fid) {
    return g_anc_remove ? g_anc_remove(g_anc_dev, gid, fid) : -1;
}

int WrapperSetActiveGroup(struct fingerprint_device*, uint32_t gid, const char* store_path) {
    return g_anc_set_active_group ? g_anc_set_active_group(g_anc_dev, gid, store_path) : -1;
}

int WrapperAuthenticate(struct fingerprint_device*, uint64_t operation_id, uint32_t /*gid*/) {
    return g_anc_authenticate ? g_anc_authenticate(g_anc_dev, operation_id) : -1;
}

// Resolves and caches every AncXxx entry point used by the wrapper, and
// builds the fingerprint_device_t the rest of the HAL will interact with.
fingerprint_device_t* CreateAncWrapper(void* anc_handle, void* jiiov_dev) {
    g_anc_handle = anc_handle;
    g_anc_dev = jiiov_dev;

    g_anc_pre_enroll = reinterpret_cast<AncU64Fn>(dlsym(anc_handle, "AncPreEnroll"));
    g_anc_enroll = reinterpret_cast<int (*)(void*, const uint8_t*, uint32_t, uint32_t)>(
            dlsym(anc_handle, "AncEnroll"));
    g_anc_post_enroll = reinterpret_cast<AncSimpleFn>(dlsym(anc_handle, "AncPostEnroll"));
    g_anc_get_authenticator_id =
            reinterpret_cast<AncU64Fn>(dlsym(anc_handle, "AncGetAuthenticatorId"));
    g_anc_cancel = reinterpret_cast<AncSimpleFn>(dlsym(anc_handle, "AncCancel"));
    g_anc_enumerate = reinterpret_cast<AncSimpleFn>(dlsym(anc_handle, "AncEnumerate"));
    g_anc_remove =
            reinterpret_cast<int (*)(void*, uint32_t, uint32_t)>(dlsym(anc_handle, "AncRemove"));
    g_anc_set_active_group = reinterpret_cast<int (*)(void*, uint32_t, const char*)>(
            dlsym(anc_handle, "AncSetActiveGroup"));
    g_anc_authenticate =
            reinterpret_cast<int (*)(void*, uint64_t)>(dlsym(anc_handle, "AncAuthenticate"));

    auto set_notify_cb = reinterpret_cast<int (*)(void*, JiiovNotify*)>(
            dlsym(anc_handle, "AncSetNotifyCallback"));
    if (set_notify_cb) {
        set_notify_cb(jiiov_dev, &g_jiiov_notify);
    }

    auto* dev = (fingerprint_device_t*)malloc(sizeof(fingerprint_device_t));
    memset(dev, 0, sizeof(fingerprint_device_t));

    dev->common.tag = HARDWARE_DEVICE_TAG;
    dev->common.version = FINGERPRINT_MODULE_API_VERSION_2_1;
    dev->common.module = (hw_module_t*)malloc(sizeof(hw_module_t));
    memset(dev->common.module, 0, sizeof(hw_module_t));

    dev->set_notify = WrapperSetNotify;
    dev->pre_enroll = WrapperPreEnroll;
    dev->enroll = WrapperEnroll;
    dev->post_enroll = WrapperPostEnroll;
    dev->get_authenticator_id = WrapperGetAuthenticatorId;
    dev->cancel = WrapperCancel;
    dev->enumerate = WrapperEnumerate;
    dev->remove = WrapperRemove;
    dev->set_active_group = WrapperSetActiveGroup;
    dev->authenticate = WrapperAuthenticate;
    dev->reserved[0] = jiiov_dev;  // TODO: confirm a real consumer reads this
                                   // (UDFPS handler?) before relying on it;
                                   // otherwise this should be dropped.

    return dev;
}

}  // namespace

fingerprint_device_t* OpenAncHal() {
    void* anc_handle = dlopen(kAncLib, RTLD_NOW);
    if (!anc_handle) {
        ALOGE("Failed to dlopen %s: %s", kAncLib, dlerror());
        return nullptr;
    }

    auto init = reinterpret_cast<int (*)(void*, const char*)>(
            dlsym(anc_handle, "InitFingerprintDevice"));
    auto get_dev = reinterpret_cast<void* (*)(void)>(dlsym(anc_handle, "GetFingerprintDevice"));
    if (!init || !get_dev) {
        ALOGE("%s is missing required symbols (InitFingerprintDevice/GetFingerprintDevice)",
              kAncLib);
        return nullptr;
    }

    void* jiiov_dev = get_dev();
    if (!jiiov_dev) {
        ALOGE("GetFingerprintDevice() returned null");
        return nullptr;
    }

    if (init(jiiov_dev, "ancapp64") != 0 && init(jiiov_dev, "anc0307") != 0) {
        ALOGE("InitFingerprintDevice failed for both known chip variants");
        return nullptr;
    }

    fingerprint_device_t* dev = CreateAncWrapper(anc_handle, jiiov_dev);
    if (!dev ||
        dev->set_notify(
                dev, aidl::android::hardware::biometrics::fingerprint::Fingerprint::notify) != 0) {
        ALOGE("Failed to initialize anc fingerprint wrapper");
        return nullptr;
    }

    return dev;
}

}  // namespace jiiov
