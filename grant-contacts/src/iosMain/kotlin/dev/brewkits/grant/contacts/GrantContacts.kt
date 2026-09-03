package dev.brewkits.grant.contacts

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.ContactsPermissionHandler

/**
 * Entry point for initializing the Grant Contacts module.
 */
public actual object GrantContacts {
    private var isInitialized = false

    /**
     * Registers the Contacts permission handler for iOS.
     */
    public actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.CONTACTS.identifier, ContactsPermissionHandler())
        IosPermissionHandlerRegistry.register(AppGrant.READ_CONTACTS.identifier, ContactsPermissionHandler())
    }
}
