package dev.brewkits.grant

import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * JVM desktop implementation of the platform delegate factory.
 *
 * [context] is accepted for API-shape parity with Android and ignored — a JVM process has no
 * Android-style `Context`. Defaults [store] to [InMemoryGrantStore]: the OS's own privacy
 * database is already the durable "have we asked before" record, the same reasoning `iosMain`
 * and `webMain` already apply.
 */
public actual fun createPlatformDelegate(context: Any?, store: GrantStore?): PlatformGrantDelegate =
    PlatformGrantDelegate(store ?: InMemoryGrantStore())
