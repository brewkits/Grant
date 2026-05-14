package dev.brewkits.grant.contacts

/**
 * Entry point for initializing the Grant Contacts module.
 */
actual object GrantContacts {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    actual fun initialize() {
        // No-op
    }
}
