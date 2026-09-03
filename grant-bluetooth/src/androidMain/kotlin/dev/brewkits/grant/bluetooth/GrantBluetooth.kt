package dev.brewkits.grant.bluetooth

/**
 * Entry point for initializing the Grant Bluetooth module.
 */
public actual object GrantBluetooth {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    public actual fun initialize() {
        // No-op
    }
}
