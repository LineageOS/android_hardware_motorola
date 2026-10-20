/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.motorola.hardware.biometric.face.bridge;

import com.motorola.hardware.biometric.face.bridge.IFaceBridge;

/** Served by the HAL daemon; the bridge app registers itself here. */
@VintfStability
interface IFaceBridgeHost {
    void registerBridge(IFaceBridge bridge);
}
