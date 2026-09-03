package dev.brewkits.grant.contacts

/**
 * Entry point for initializing the Grant Contacts module.
 */
public actual object GrantContacts {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    public actual fun initialize() {
        // No-op
    }
}
