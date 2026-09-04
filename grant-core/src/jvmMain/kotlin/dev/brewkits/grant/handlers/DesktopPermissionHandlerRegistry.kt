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
    // ConcurrentHashMap, not the mutableMapOf() that IosPermissionHandlerRegistry uses: this
    // registry lives on the JVM, where a `register()` during startup can genuinely overlap a
    // `get()` from a permission request already running on Dispatchers.IO. The iOS registry's
    // plain map is safe there because registration happens once, on the main thread, before any
    // request — an assumption a JVM desktop app does not owe us.
    private val handlers = java.util.concurrent.ConcurrentHashMap<String, DesktopPermissionHandler>()

    /** Registers a handler for a specific permission identifier (an [dev.brewkits.grant.AppGrant.identifier] or a [dev.brewkits.grant.RawPermission] identifier). */
    public fun register(identifier: String, handler: DesktopPermissionHandler) {
        handlers[identifier] = handler
    }

    /** Retrieves a registered handler, if any. */
    internal fun get(identifier: String): DesktopPermissionHandler? {
        return handlers[identifier]
    }

    /**
     * Opens the OS's privacy settings UI, if a real one is registered — `grant-core`'s own
     * `jvmMain` never sets this (it has no AppKit dependency; see `PlatformGrantDelegate.jvm.kt`),
     * so this stays `null` unless `grant-desktop` (or an equivalent opt-in module) provides one.
     *
     * `@Volatile` for the same reason the map above is concurrent: the thread that installs this
     * during startup is not necessarily the thread that later reads it from `openSettings()`.
     */
    @Volatile
    public var settingsOpener: (() -> Unit)? = null
}
