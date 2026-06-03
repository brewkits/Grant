package dev.brewkits.grant.handlers

/**
 * A registry for developers to provide custom handlers for [dev.brewkits.grant.RawPermission] on iOS.
 * 
 * Since iOS permissions are tied to specific frameworks, the core library cannot 
 * possibly link against all of them without causing App Store rejections for missing 
 * Info.plist keys. 
 * 
 * If you need to request a permission not covered by [dev.brewkits.grant.AppGrant], 
 * you can implement [PermissionHandler] and register it here.
 */
object IosPermissionHandlerRegistry {
    private val handlers = mutableMapOf<String, PermissionHandler>()

    /**
     * Registers a custom handler for a specific RawPermission identifier.
     * 
     * @param identifier The identifier of the RawPermission.
     * @param handler The custom handler implementation.
     */
    fun register(identifier: String, handler: PermissionHandler) {
        handlers[identifier] = handler
    }

    /**
     * Retrieves a registered handler, if any.
     */
    internal fun get(identifier: String): PermissionHandler? {
        return handlers[identifier]
    }
}
