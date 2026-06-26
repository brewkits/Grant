package dev.brewkits.grant

import dev.brewkits.grant.impl.DefaultGrantManager
import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * A static factory for creating [GrantManager] instances without a Dependency
 * Injection framework.
 *
 * Use this for "Manual Injection" if your project does not use Koin or other DI tools.
 *
 * ### Usage Example (Android)
 * ```kotlin
 * val grantManager = GrantFactory.create(applicationContext)
 * ```
 *
 * ### Usage Example (iOS)
 * ```kotlin
 * val grantManager = GrantFactory.create()
 * ```
 */
object GrantFactory {
    /**
     * Creates and returns a production-ready [GrantManager] instance.
     *
     * @param context The platform-specific context (required on Android, ignored on iOS).
     * @param store The storage implementation for tracking request history. When `null`
     *   (the default), the platform default is used: a persistent
     *   `SharedPreferencesGrantStore` on Android (so request history survives process
     *   death — see Issue #55) and [InMemoryGrantStore] on iOS.
     * @return A fully configured [GrantManager].
     */
    fun create(
        context: Any? = null,
        store: GrantStore? = null
    ): GrantManager {
        val delegate = createPlatformDelegate(context, store)
        return DefaultGrantManager(delegate)
    }
}

/**
 * Platform-specific factory function. Internal use only.
 *
 * A `null` [store] selects the platform default (persistent on Android, in-memory on iOS).
 */
expect fun createPlatformDelegate(context: Any?, store: GrantStore?): PlatformGrantDelegate
