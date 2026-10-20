/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "face-moto"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include "BridgeHost.h"
#include "Face.h"

using aidl::android::hardware::biometrics::face::Face;
using com::motorola::hardware::biometric::face::BridgeHost;

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    ABinderProcess_startThreadPool();

    auto face = ndk::SharedRefBase::make<Face>();
    const std::string faceName = std::string(Face::descriptor) + "/default";
    CHECK_EQ(STATUS_OK, AServiceManager_addService(face->asBinder().get(), faceName.c_str()));

    auto host = BridgeHost::get();
    const std::string hostName = std::string(aidl::com::motorola::hardware::biometric::face::
                                                     bridge::IFaceBridgeHost::descriptor) +
                                 "/default";
    binder_status_t st = AServiceManager_addService(host->asBinder().get(), hostName.c_str());
    if (st != STATUS_OK) {
        // Most likely the VINTF fragment is not installed. Keep serving IFace (it will report
        // HW_UNAVAILABLE) instead of crash-looping the whole HAL.
        LOG(ERROR) << "cannot register " << hostName << " (" << st
                   << "); is its VINTF fragment installed in /vendor/etc/vintf/manifest?";
    }

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;
}
