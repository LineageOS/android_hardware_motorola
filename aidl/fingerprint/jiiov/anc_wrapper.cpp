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

template <typename Fn>
void Resolve(void* handle, const char* symbol, Fn* out) {
    *out = reinterpret_cast<Fn>(dlsym(handle, symbol));
}

// Forwards to a cached vendor function pointer if it resolved, or returns
// `fallback` otherwise. Collapses the "is this null? call it : fallback"
// ternary that would otherwise be repeated in every WrapperXxx below.
template <typename Fn, typename Ret, typename... Args>
Ret CallAnc(Fn fn, Ret fallback, Args... args) {
    return fn ? fn(args...) : fallback;
}

// Dispatches a translated message to the framework, if a notify callback
// has been registered. Centralizes the null-check that would otherwise be
// repeated in every AncOnXxx translator below.
void NotifyLegacy(fingerprint_msg_t* msg) {
    if (g_legacy_notify) g_legacy_notify(msg);
}

// --- Notify translation: JiiovNotify -> fingerprint_msg_t ---

void AncOnEnrollResult(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_ENROLLING;
    msg.data.enroll.finger.fid = finger_id;
    msg.data.enroll.finger.gid = group_id;
    msg.data.enroll.samples_remaining = remaining;
    NotifyLegacy(&msg);
}

void AncOnAcquired(void*, uint32_t acquiredInfo) {
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_ACQUIRED;
    msg.data.acquired.acquired_info = (fingerprint_acquired_info_t)acquiredInfo;
    NotifyLegacy(&msg);
}

void AncOnAuthenticated(void*, uint32_t finger_id, uint32_t group_id, const hw_auth_token_t* token,
                        size_t /*len*/) {
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_AUTHENTICATED;
    msg.data.authenticated.finger.fid = finger_id;
    msg.data.authenticated.finger.gid = group_id;
    if (token) {
        msg.data.authenticated.hat = *token;
    }
    NotifyLegacy(&msg);
}

void AncOnError(void*, uint32_t error, uint32_t /*vendorCode*/) {
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_ERROR;
    msg.data.error = (fingerprint_error_t)error;
    // vendorCode is dropped: the legacy Moto HAL error type is a plain enum
    // with no room for a vendor-specific sub-code.
    NotifyLegacy(&msg);
}

void AncOnRemoved(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_REMOVED;
    msg.data.removed.finger.fid = finger_id;
    msg.data.removed.finger.gid = group_id;
    msg.data.removed.remaining_templates = remaining;
    NotifyLegacy(&msg);
}

void AncOnEnumerate(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_ENUMERATING;
    msg.data.enumerated.finger.fid = finger_id;
    msg.data.enumerated.finger.gid = group_id;
    msg.data.enumerated.remaining_templates = remaining;
    NotifyLegacy(&msg);
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
    return CallAnc(g_anc_pre_enroll, uint64_t{0}, g_anc_dev);
}

int WrapperEnroll(struct fingerprint_device*, const hw_auth_token_t* hat, uint32_t gid,
                  uint32_t timeout_sec) {
    return CallAnc(g_anc_enroll, -1, g_anc_dev, (const uint8_t*)hat, gid, timeout_sec);
}

int WrapperPostEnroll(struct fingerprint_device*) {
    return CallAnc(g_anc_post_enroll, -1, g_anc_dev);
}

uint64_t WrapperGetAuthenticatorId(struct fingerprint_device*) {
    return CallAnc(g_anc_get_authenticator_id, uint64_t{0}, g_anc_dev);
}

int WrapperCancel(struct fingerprint_device*) {
    return CallAnc(g_anc_cancel, -1, g_anc_dev);
}

int WrapperEnumerate(struct fingerprint_device*) {
    return CallAnc(g_anc_enumerate, -1, g_anc_dev);
}

int WrapperRemove(struct fingerprint_device*, uint32_t gid, uint32_t fid) {
    return CallAnc(g_anc_remove, -1, g_anc_dev, gid, fid);
}

int WrapperSetActiveGroup(struct fingerprint_device*, uint32_t gid, const char* store_path) {
    return CallAnc(g_anc_set_active_group, -1, g_anc_dev, gid, store_path);
}

int WrapperAuthenticate(struct fingerprint_device*, uint64_t operation_id, uint32_t /*gid*/) {
    return CallAnc(g_anc_authenticate, -1, g_anc_dev, operation_id);
}

// Resolves and caches every AncXxx entry point used by the wrapper, and
// builds the fingerprint_device_t the rest of the HAL will interact with.
fingerprint_device_t* CreateAncWrapper(void* anc_handle, void* jiiov_dev) {
    g_anc_handle = anc_handle;
    g_anc_dev = jiiov_dev;

    Resolve(anc_handle, "AncPreEnroll", &g_anc_pre_enroll);
    Resolve(anc_handle, "AncEnroll", &g_anc_enroll);
    Resolve(anc_handle, "AncPostEnroll", &g_anc_post_enroll);
    Resolve(anc_handle, "AncGetAuthenticatorId", &g_anc_get_authenticator_id);
    Resolve(anc_handle, "AncCancel", &g_anc_cancel);
    Resolve(anc_handle, "AncEnumerate", &g_anc_enumerate);
    Resolve(anc_handle, "AncRemove", &g_anc_remove);
    Resolve(anc_handle, "AncSetActiveGroup", &g_anc_set_active_group);
    Resolve(anc_handle, "AncAuthenticate", &g_anc_authenticate);

    int (*set_notify_cb)(void*, JiiovNotify*) = nullptr;
    Resolve(anc_handle, "AncSetNotifyCallback", &set_notify_cb);
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
        dlclose(anc_handle);
        return nullptr;
    }

    void* jiiov_dev = get_dev();
    if (!jiiov_dev) {
        ALOGE("GetFingerprintDevice() returned null");
        dlclose(anc_handle);
        return nullptr;
    }

    if (init(jiiov_dev, "ancapp64") != 0 && init(jiiov_dev, "anc0307") != 0) {
        ALOGE("InitFingerprintDevice failed for both known chip variants");
        dlclose(anc_handle);
        return nullptr;
    }

    fingerprint_device_t* dev = CreateAncWrapper(anc_handle, jiiov_dev);
    if (!dev ||
        dev->set_notify(
                dev, aidl::android::hardware::biometrics::fingerprint::Fingerprint::notify) != 0) {
        ALOGE("Failed to initialize anc fingerprint wrapper");
        dlclose(anc_handle);
        return nullptr;
    }

    return dev;
}

}  // namespace jiiov
