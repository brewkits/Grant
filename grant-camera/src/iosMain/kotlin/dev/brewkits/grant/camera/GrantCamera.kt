package dev.brewkits.grant.camera

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.AVPermissionHandler
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry

/** iOS: registers the AVFoundation-backed handlers for [AppGrant.CAMERA] and [AppGrant.MICROPHONE]. */
public actual object GrantCamera {
    private var isInitialized = false

    public actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.CAMERA.identifier, AVPermissionHandler.camera())
        IosPermissionHandlerRegistry.register(AppGrant.MICROPHONE.identifier, AVPermissionHandler.microphone())
    }
}
