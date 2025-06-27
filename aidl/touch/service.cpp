/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.touch-service.motorola"

#include "HighTouchPollingRate.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using aidl::vendor::lineage::touch::HighTouchPollingRate;

int main() {
    binder_status_t status = STATUS_OK;

    ABinderProcess_setThreadPoolMaxThreadCount(0);

#ifdef HTPR_CONTROL_PATH
    std::shared_ptr<HighTouchPollingRate> htpr = ndk::SharedRefBase::make<HighTouchPollingRate>();
    const std::string htpr_instance = std::string(HighTouchPollingRate::descriptor) + "/default";
    status = AServiceManager_addService(htpr->asBinder().get(), htpr_instance.c_str());
    CHECK_EQ(status, STATUS_OK) << "Failed to add service " << htpr_instance << " " << status;
#endif

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}
