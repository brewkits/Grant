package dev.brewkits.grant

import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * The browser has no "context" concept — [context] is accepted for API-shape parity with
 * Android/iOS and ignored. `store` defaults to [InMemoryGrantStore] and, unlike Android, that
 * default is also the *correct* choice on the web: the browser's own permission record is
 * already durable across page loads, so there is no process-death-style gap for a persistent
 * store to close here.
 */
public actual fun createPlatformDelegate(context: Any?, store: GrantStore?): PlatformGrantDelegate =
    PlatformGrantDelegate(store ?: InMemoryGrantStore())
