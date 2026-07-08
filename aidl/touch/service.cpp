/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.touch-service.motorola"

#include "HighTouchPollingRate.h"
#ifdef SINGLE_TAP_CONTROL_PATH
#include "TouchscreenGesture.h"
#endif

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using aidl::vendor::lineage::touch::HighTouchPollingRate;
using aidl::vendor::lineage::touch::TouchscreenGesture;

int main() {
    binder_status_t status = STATUS_OK;

    ABinderProcess_setThreadPoolMaxThreadCount(0);

#ifdef HTPR_CONTROL_PATH
    std::shared_ptr<HighTouchPollingRate> htpr = ndk::SharedRefBase::make<HighTouchPollingRate>();
    const std::string htpr_instance = std::string(HighTouchPollingRate::descriptor) + "/default";
    status = AServiceManager_addService(htpr->asBinder().get(), htpr_instance.c_str());
    CHECK_EQ(status, STATUS_OK) << "Failed to add service " << htpr_instance << " " << status;
#endif

#ifdef SINGLE_TAP_CONTROL_PATH
    std::shared_ptr<TouchscreenGesture> tg = ndk::SharedRefBase::make<TouchscreenGesture>();
    const std::string gesture_instance = std::string(TouchscreenGesture::descriptor) + "/default";
    status = AServiceManager_addService(tg->asBinder().get(), gesture_instance.c_str());
    CHECK_EQ(status, STATUS_OK) << "Failed to add service " << gesture_instance << " " << status;
#endif

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}
