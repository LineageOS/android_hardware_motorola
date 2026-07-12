/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "UdfpsHandler.h"
#include <android-base/properties.h>
#include <dlfcn.h>

#define UDFPS_HANDLER_DEFAULT "libudfpshandler"
#define UDFPS_HANDLER_FACTORY "UDFPS_HANDLER_FACTORY"

UdfpsHandlerFactory* getUdfpsHandlerFactory() {
    void* libudfpshander = nullptr;

    auto tryLoad = [&](const std::string& name) -> UdfpsHandlerFactory* {
        std::string lib = name + ".so";
        libudfpshander = dlopen(lib.c_str(), RTLD_LAZY);
        if (!libudfpshander) return nullptr;

        auto* factory_handler = reinterpret_cast<UdfpsHandlerFactory*>(
                dlsym(libudfpshander, UDFPS_HANDLER_FACTORY));

        if (!factory_handler) {
            dlclose(libudfpshander);
            libudfpshander = nullptr;
            return nullptr;
        }
        return factory_handler;
    };

    auto variant = android::base::GetProperty("persist.vendor.fingerprint.udfps.handler", "");
    if (!variant.empty()) {
        auto* factory_handler = tryLoad(std::string(UDFPS_HANDLER_DEFAULT) + "_" + variant);
        if (factory_handler) return factory_handler;
    }
    return tryLoad(UDFPS_HANDLER_DEFAULT);
}
