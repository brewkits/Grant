package dev.brewkits.grant.location

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.LocationAlwaysPermissionHandler
import dev.brewkits.grant.delegates.LocationAlwaysManagerDelegate

/**
 * Entry point for initializing the Grant LocationAlways module.
 */
actual object GrantLocationAlways {
    private var isInitialized = false
    private val delegate by lazy { LocationAlwaysManagerDelegate() }

    /**
     * Registers the LocationAlways permission handler for iOS.
     */
    actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.LOCATION_ALWAYS.identifier, LocationAlwaysPermissionHandler(delegate))
    }
}
