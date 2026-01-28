package dev.brewkits.grant

import dev.brewkits.grant.impl.MyGrantManager
import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * Factory to create GrantManager instances without dependency injection framework.
 *
 * **Use this if you don't want to use Koin or any DI framework.**
 *
 * ## Usage
 *
 * ### Android:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private val grantManager by lazy {
 *         GrantFactory.create(applicationContext)
 *         // Uses InMemoryGrantStore (default) - aligns with 90% of libraries
 *     }
 * }
 * ```
 *
 * ### iOS:
 * ```kotlin
 * class ViewController : UIViewController {
 *     private val grantManager = GrantFactory.create()
 *     // Uses InMemoryGrantStore by default
 * }
 * ```
 *
 * ### Custom Store (Advanced):
 * If you need custom storage behavior, implement [GrantStore]:
 * ```kotlin
 * class MyCustomStore : GrantStore {
 *     // Your implementation
 * }
 *
 * val grantManager = GrantFactory.create(
 *     context = applicationContext,
 *     store = MyCustomStore()
 * )
 * ```
 *
 * ### With Koin (Optional):
 * If you prefer using Koin for dependency injection:
 * ```kotlin
 * startKoin {
 *     modules(grantModule, grantPlatformModule)
 * }
 * ```
 *
 * **Note**: The factory pattern is recommended for libraries and standalone apps.
 * Koin integration is optional and only needed if your project already uses Koin.
 */
object GrantFactory {
    /**
     * Create a GrantManager instance.
     *
     * @param context Platform-specific context:
     *                - Android: android.content.Context (required)
     *                - iOS: Not needed (pass null or omit)
     * @param store Storage implementation for permission state (default: InMemoryGrantStore).
     *              Uses in-memory storage following industry standards (90% of libraries).
     *              Implement custom [GrantStore] if you need different behavior.
     * @return GrantManager instance ready to use
     *
     * @throws IllegalArgumentException on Android if context is not provided
     */
    fun create(
        context: Any? = null,
        store: GrantStore = InMemoryGrantStore()
    ): GrantManager {
        val delegate = createPlatformDelegate(context, store)
        return MyGrantManager(delegate)
    }
}

/**
 * Platform-specific factory function to create PlatformGrantDelegate.
 *
 * This is implemented separately for each platform:
 * - Android: Requires Context and GrantStore
 * - iOS: Requires GrantStore only
 *
 * @param context Platform context (Android only)
 * @param store Storage implementation for permission state
 */
expect fun createPlatformDelegate(context: Any?, store: GrantStore): PlatformGrantDelegate
