package dev.brewkits.grant.bluetooth

/**
 * Entry point for initializing the Grant Bluetooth module.
 */
actual object GrantBluetooth {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    actual fun initialize() {
        // No-op
    }
}
