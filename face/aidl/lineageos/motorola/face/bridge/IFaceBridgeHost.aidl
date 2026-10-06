package lineageos.motorola.face.bridge;

import lineageos.motorola.face.bridge.IFaceBridge;

/** Served by the HAL daemon; the bridge app registers itself here. */
@VintfStability
interface IFaceBridgeHost {
    void registerBridge(IFaceBridge bridge);
}
