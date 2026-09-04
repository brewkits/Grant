package dev.brewkits.grant.handlers

/**
 * A registry real platform modules register into, mirroring `IosPermissionHandlerRegistry`.
 *
 * `grant-core`'s own `jvmMain` never populates this — it has no macOS/Windows framework
 * dependency and never will (that is the point of isolating them). The `grant-desktop` module
 * (opt-in, carries JNA + the macOS TCC bridge) calls [register] from its `initialize()`, the
 * same pattern `grant-contacts`/`grant-calendar`/`grant-motion` already use for iOS. A consumer
 * that never adds `grant-desktop` sees every desktop-only [dev.brewkits.grant.AppGrant]
 * resolve to `GrantStatus.DENIED_ALWAYS` — an honest "not backed on this platform", never a
 * fabricated grant.
 */
public object DesktopPermissionHandlerRegistry {
    private val handlers = mutableMapOf<String, DesktopPermissionHandler>()

    /** Registers a handler for a specific permission identifier (an [dev.brewkits.grant.AppGrant.identifier] or a [dev.brewkits.grant.RawPermission] identifier). */
    public fun register(identifier: String, handler: DesktopPermissionHandler) {
        handlers[identifier] = handler
    }

    /** Retrieves a registered handler, if any. */
    internal fun get(identifier: String): DesktopPermissionHandler? {
        return handlers[identifier]
    }
}
