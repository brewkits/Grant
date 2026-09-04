package dev.brewkits.grant.tracking

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.TrackingPermissionHandler

/**
 * Entry point for initializing the Grant Tracking module.
 */
public actual object GrantTracking {
    private var isInitialized = false

    /**
     * Registers the App Tracking Transparency handler for iOS.
     */
    public actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.APP_TRACKING.identifier, TrackingPermissionHandler())
    }
}
