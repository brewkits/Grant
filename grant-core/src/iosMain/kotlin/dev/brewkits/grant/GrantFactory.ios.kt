package dev.brewkits.grant

import dev.brewkits.grant.handlers.IosPermissionHandler
import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * iOS implementation of platform delegate factory.
 *
 * Accepts GrantStore for state management (in-memory only on iOS).
 *
 * This legacy factory registers every built-in handler so existing consumers
 * remain functional. Apps that want K/N DCE to strip unused handlers should
 * switch to [GrantFactory.create] with the block form.
 */
actual fun createPlatformDelegate(context: Any?, store: GrantStore): PlatformGrantDelegate {
    // iOS doesn't use context, only store
    return PlatformGrantDelegate(store)
}

actual fun createPlatformDelegateWithRegistry(
    context: Any?,
    store: GrantStore,
    registrations: Map<AppGrant, () -> Any>
): PlatformGrantDelegate {
    @Suppress("UNCHECKED_CAST")
    val typedRegistrations: Map<AppGrant, () -> IosPermissionHandler> =
        registrations.mapValues { (_, factory) ->
            { factory() as IosPermissionHandler }
        }
    return PlatformGrantDelegate(store, typedRegistrations)
}
