package dev.brewkits.grant.location

/**
 * Entry point for initializing the Grant LocationAlways module.
 */
actual object GrantLocationAlways {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    actual fun initialize() {
        // No-op
    }
}
