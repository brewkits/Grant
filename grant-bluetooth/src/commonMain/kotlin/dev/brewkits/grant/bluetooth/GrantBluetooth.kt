package dev.brewkits.grant.bluetooth

/**
 * Entry point for initializing the Grant Bluetooth module.
 */
expect object GrantBluetooth {
    /**
     * Initializes the Bluetooth permission handler.
     * Must be called before requesting Bluetooth permissions.
     * It is safe to call this multiple times.
     */
    fun initialize()
}
