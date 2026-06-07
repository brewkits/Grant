package dev.brewkits.grant.bluetooth

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.BluetoothPermissionHandler

import dev.brewkits.grant.delegates.BluetoothManagerDelegate

/**
 * Entry point for initializing the Grant Bluetooth module.
 */
actual object GrantBluetooth {
    private var isInitialized = false
    private val delegate by lazy { BluetoothManagerDelegate() }

    /**
     * Registers the Bluetooth permission handler for iOS.
     */
    actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.BLUETOOTH.identifier, BluetoothPermissionHandler(delegate))
        IosPermissionHandlerRegistry.register(AppGrant.BLUETOOTH_ADVERTISE.identifier, BluetoothPermissionHandler(delegate))
    }
}
