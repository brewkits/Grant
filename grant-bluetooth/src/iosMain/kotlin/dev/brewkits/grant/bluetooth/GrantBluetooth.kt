package dev.brewkits.grant.bluetooth

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.BluetoothPermissionHandler

import dev.brewkits.grant.delegates.BluetoothManagerDelegate

/**
 * Entry point for initializing the Grant Bluetooth module.
 */
public actual object GrantBluetooth {
    private var isInitialized = false
    private val delegate by lazy { BluetoothManagerDelegate() }

    /**
     * Registers the Bluetooth permission handler for iOS.
     */
    public actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        // One handler instance for all four: iOS exposes a single Bluetooth authorization
        // (CBManager.authorization()) covering scan, connect and advertise. The
        // scan/connect split exists only on Android, from API 31 onward.
        val handler = BluetoothPermissionHandler(delegate)
        IosPermissionHandlerRegistry.register(AppGrant.BLUETOOTH.identifier, handler)
        IosPermissionHandlerRegistry.register(AppGrant.BLUETOOTH_SCAN.identifier, handler)
        IosPermissionHandlerRegistry.register(AppGrant.BLUETOOTH_CONNECT.identifier, handler)
        IosPermissionHandlerRegistry.register(AppGrant.BLUETOOTH_ADVERTISE.identifier, handler)
    }
}
