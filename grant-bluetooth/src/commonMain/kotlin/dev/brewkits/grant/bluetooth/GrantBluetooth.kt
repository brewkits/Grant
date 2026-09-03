package dev.brewkits.grant.bluetooth

/**
 * Entry point for initializing the Grant Bluetooth module.
 */
public expect object GrantBluetooth {
    /**
     * Initializes the Bluetooth permission handler.
     * Must be called before requesting Bluetooth permissions.
     * It is safe to call this multiple times.
     */
    public fun initialize()
}
