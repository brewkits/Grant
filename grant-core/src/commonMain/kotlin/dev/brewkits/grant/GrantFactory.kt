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
 *     private val GrantManager by lazy {
 *         GrantFactory.create(applicationContext)
 *     }
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         // Use GrantManager
 *     }
 * }
 * ```
 *
 * ### iOS:
 * ```kotlin
 * class ViewController : UIViewController {
 *     private val GrantManager = GrantFactory.create()
 *
 *     override fun viewDidLoad() {
 *         super.viewDidLoad()
 *         // Use GrantManager
 *     }
 * }
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
     * @return GrantManager instance ready to use
     *
     * @throws IllegalArgumentException on Android if context is not provided
     */
    fun create(context: Any? = null): GrantManager {
        val delegate = createPlatformDelegate(context)
        return MyGrantManager(delegate)
    }
}

/**
 * Platform-specific factory function to create PlatformGrantDelegate.
 *
 * This is implemented separately for each platform:
 * - Android: Requires Context
 * - iOS: No parameters needed
 */
expect fun createPlatformDelegate(context: Any?): PlatformGrantDelegate
