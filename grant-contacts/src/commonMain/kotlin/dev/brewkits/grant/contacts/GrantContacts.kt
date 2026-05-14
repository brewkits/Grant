package dev.brewkits.grant.contacts

/**
 * Entry point for initializing the Grant Contacts module.
 */
expect object GrantContacts {
    /**
     * Initializes the Contacts permission handler.
     * Must be called before requesting Contacts permissions.
     * It is safe to call this multiple times.
     */
    fun initialize()
}
