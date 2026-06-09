#pragma once
// Removed <hardware/fingerprint.h> to prevent conflict with Moto's fingerprint.h included by Fingerprint.cpp
#include <hardware/hardware.h>
#include <dlfcn.h>
#include <log/log.h>
#include <stdlib.h>

struct JiiovNotify {
    void (*OnChallengeGenerated)(void* instance, uint64_t challenge);
    void (*OnChallengeRevoked)(void* instance, uint64_t challenge);
    void (*OnAuthenticatorIdRetrieved)(void* instance, uint64_t id);
    void (*OnAuthenticatorIdInvalidated)(void* instance, uint64_t id);
    void (*OnEnrollResult)(void* instance, uint32_t finger_id, uint32_t group_id, uint32_t remaining);
    void (*OnAcquired)(void* instance, uint32_t acquiredInfo);
    void (*OnAuthenticated)(void* instance, uint32_t finger_id, uint32_t group_id, const hw_auth_token_t* token, size_t len);
    void (*OnLockoutTimed)(void* instance, uint32_t duration);
    void (*OnLockoutPermanent)(void* instance);
    void (*OnError)(void* instance, uint32_t error, uint32_t vendorCode);
    void (*OnRemoved)(void* instance, uint32_t finger_id, uint32_t group_id, uint32_t remaining);
    void (*OnEnumerate)(void* instance, uint32_t finger_id, uint32_t group_id, uint32_t remaining);
};

static fingerprint_notify_t g_legacy_notify = nullptr;
static void* g_anc_dev = nullptr;

static void anc_OnEnrollResult(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_ENROLLING;
    msg.data.enroll.finger.fid = finger_id;
    msg.data.enroll.finger.gid = group_id;
    msg.data.enroll.samples_remaining = remaining;
    g_legacy_notify(&msg);
}

static void anc_OnAcquired(void*, uint32_t acquiredInfo) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_ACQUIRED;
    msg.data.acquired.acquired_info = (fingerprint_acquired_info_t)acquiredInfo;
    g_legacy_notify(&msg);
}

static void anc_OnAuthenticated(void*, uint32_t finger_id, uint32_t group_id, const hw_auth_token_t* token, size_t len) {
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

static void anc_OnError(void*, uint32_t error, uint32_t vendorCode) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_ERROR;
    msg.data.error = (fingerprint_error_t)error;
    // vendorCode dropped since legacy Moto HAL error is just an enum
    g_legacy_notify(&msg);
}

static void anc_OnRemoved(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_REMOVED;
    msg.data.removed.finger.fid = finger_id;
    msg.data.removed.finger.gid = group_id;
    msg.data.removed.remaining_templates = remaining;
    g_legacy_notify(&msg);
}

static void anc_OnEnumerate(void*, uint32_t finger_id, uint32_t group_id, uint32_t remaining) {
    if (!g_legacy_notify) return;
    fingerprint_msg_t msg = {};
    msg.type = FINGERPRINT_TEMPLATE_ENUMERATING;
    msg.data.enumerated.finger.fid = finger_id;
    msg.data.enumerated.finger.gid = group_id;
    msg.data.enumerated.remaining_templates = remaining;
    g_legacy_notify(&msg);
}

// Ignore unused callbacks
static void anc_ignore(void*, ...) {}
static void anc_ignore0(void*) {}

static JiiovNotify g_jiiov_notify = {
    .OnChallengeGenerated = (void (*)(void*, uint64_t))anc_ignore,
    .OnChallengeRevoked = (void (*)(void*, uint64_t))anc_ignore,
    .OnAuthenticatorIdRetrieved = (void (*)(void*, uint64_t))anc_ignore,
    .OnAuthenticatorIdInvalidated = (void (*)(void*, uint64_t))anc_ignore,
    .OnEnrollResult = anc_OnEnrollResult,
    .OnAcquired = anc_OnAcquired,
    .OnAuthenticated = anc_OnAuthenticated,
    .OnLockoutTimed = (void (*)(void*, uint32_t))anc_ignore,
    .OnLockoutPermanent = anc_ignore0,
    .OnError = anc_OnError,
    .OnRemoved = anc_OnRemoved,
    .OnEnumerate = anc_OnEnumerate,
};

// Legacy Wrapper methods
static int wrapper_set_notify(struct fingerprint_device *dev, fingerprint_notify_t notify) {
    g_legacy_notify = notify;
    return 0;
}

static uint64_t wrapper_pre_enroll(struct fingerprint_device *dev) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return 0;
    uint64_t (*f)(void*) = (uint64_t (*)(void*))dlsym(anc_handle, "AncPreEnroll");
    return f ? f(g_anc_dev) : 0;
}

static int wrapper_enroll(struct fingerprint_device *dev, const hw_auth_token_t *hat, uint32_t gid, uint32_t timeout_sec) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*, const uint8_t*, uint32_t, uint32_t) = (int (*)(void*, const uint8_t*, uint32_t, uint32_t))dlsym(anc_handle, "AncEnroll");
    return f ? f(g_anc_dev, (const uint8_t*)hat, gid, timeout_sec) : -1;
}

static int wrapper_post_enroll(struct fingerprint_device *dev) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*) = (int (*)(void*))dlsym(anc_handle, "AncPostEnroll");
    return f ? f(g_anc_dev) : -1;
}

static uint64_t wrapper_get_authenticator_id(struct fingerprint_device *dev) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return 0;
    uint64_t (*f)(void*) = (uint64_t (*)(void*))dlsym(anc_handle, "AncGetAuthenticatorId");
    return f ? f(g_anc_dev) : 0;
}

static int wrapper_cancel(struct fingerprint_device *dev) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*) = (int (*)(void*))dlsym(anc_handle, "AncCancel");
    return f ? f(g_anc_dev) : -1;
}

static int wrapper_enumerate(struct fingerprint_device *dev) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*) = (int (*)(void*))dlsym(anc_handle, "AncEnumerate");
    return f ? f(g_anc_dev) : -1;
}

static int wrapper_remove(struct fingerprint_device *dev, uint32_t gid, uint32_t fid) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*, uint32_t, uint32_t) = (int (*)(void*, uint32_t, uint32_t))dlsym(anc_handle, "AncRemove");
    return f ? f(g_anc_dev, gid, fid) : -1;
}

static int wrapper_set_active_group(struct fingerprint_device *dev, uint32_t gid, const char *store_path) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*, uint32_t, const char*) = (int (*)(void*, uint32_t, const char*))dlsym(anc_handle, "AncSetActiveGroup");
    return f ? f(g_anc_dev, gid, store_path) : -1;
}

static int wrapper_authenticate(struct fingerprint_device *dev, uint64_t operation_id, uint32_t gid) {
    void* anc_handle = dlopen("anc.hal.so", RTLD_NOW);
    if (!anc_handle) return -1;
    int (*f)(void*, uint64_t) = (int (*)(void*, uint64_t))dlsym(anc_handle, "AncAuthenticate");
    return f ? f(g_anc_dev, operation_id) : -1;
}

static fingerprint_device_t* create_anc_wrapper(void* anc_handle, void* jiiov_dev) {
    g_anc_dev = jiiov_dev;
    
    // Set up notifications
    int (*set_notify_cb)(void*, JiiovNotify*) = (int (*)(void*, JiiovNotify*))dlsym(anc_handle, "AncSetNotifyCallback");
    if (set_notify_cb) {
        set_notify_cb(jiiov_dev, &g_jiiov_notify);
    }
    
    // Allocate the wrapper device
    fingerprint_device_t* dev = (fingerprint_device_t*)malloc(sizeof(fingerprint_device_t));
    memset(dev, 0, sizeof(fingerprint_device_t));
    
    dev->common.tag = HARDWARE_DEVICE_TAG;
    dev->common.version = FINGERPRINT_MODULE_API_VERSION_2_1;
    dev->common.module = (hw_module_t*)malloc(sizeof(hw_module_t));
    memset(dev->common.module, 0, sizeof(hw_module_t));
    
    dev->set_notify = wrapper_set_notify;
    dev->pre_enroll = wrapper_pre_enroll;
    dev->enroll = wrapper_enroll;
    dev->post_enroll = wrapper_post_enroll;
    dev->get_authenticator_id = wrapper_get_authenticator_id;
    dev->cancel = wrapper_cancel;
    dev->enumerate = wrapper_enumerate;
    dev->remove = wrapper_remove;
    dev->set_active_group = wrapper_set_active_group;
    dev->authenticate = wrapper_authenticate;
    dev->reserved[0] = jiiov_dev; // Pass the context back for UDFPS Handler
    
    return dev;
}
