package dev.brewkits.grant.motion

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.MotionPermissionHandler

/**
 * Entry point for initializing the Grant Motion module.
 */
actual object GrantMotion {
    private var isInitialized = false

    /**
     * Registers the Motion permission handler for iOS.
     */
    actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.MOTION.identifier, MotionPermissionHandler())
    }
}
