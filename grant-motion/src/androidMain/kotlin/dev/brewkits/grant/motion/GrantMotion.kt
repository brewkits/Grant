package dev.brewkits.grant.motion

/**
 * Entry point for initializing the Grant Motion module.
 */
public actual object GrantMotion {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    public actual fun initialize() {
        // No-op
    }
}
