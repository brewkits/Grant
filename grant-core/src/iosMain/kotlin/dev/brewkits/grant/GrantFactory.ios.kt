package dev.brewkits.grant

import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * iOS implementation of platform delegate factory.
 *
 * Accepts GrantStore for state management (in-memory only on iOS).
 */
actual fun createPlatformDelegate(context: Any?, store: GrantStore): PlatformGrantDelegate {
    // iOS doesn't use context, only store
    return PlatformGrantDelegate(store)
}
