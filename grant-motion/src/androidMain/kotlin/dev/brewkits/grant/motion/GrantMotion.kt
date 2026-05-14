package dev.brewkits.grant.motion

/**
 * Entry point for initializing the Grant Motion module.
 */
actual object GrantMotion {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    actual fun initialize() {
        // No-op
    }
}
