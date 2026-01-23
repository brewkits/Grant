package dev.brewkits.grant.di

import dev.brewkits.grant.GrantAndServiceChecker
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.ServiceManager
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.impl.MyGrantManager
import dev.brewkits.grant.impl.PlatformGrantDelegate
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin DI module for grant management.
 *
 * Provides production-ready, platform-specific grant implementation.
 *
 * **Architecture**:
 * - GrantManager: Handles grant requests
 * - ServiceManager: Checks system services (GPS, Bluetooth, etc.)
 * - GrantAndServiceChecker: Convenience API combining both
 *
 * **Usage**:
 * ```kotlin
 * // In your Koin setup
 * startKoin {
 *     modules(
 *         grantModule,
 *         grantPlatformModule // Platform-specific (Android/iOS)
 *     )
 * }
 * ```
 */
val grantModule = module {
    // Grant Manager
    single<GrantManager> {
        MyGrantManager(
            platformDelegate = get() // Provided by platform-specific module
        )
    }

    // Service Manager
    single<ServiceManager> {
        ServiceFactory.createServiceManager()
    }

    // Combined Checker (convenience)
    single {
        GrantAndServiceChecker(
            grantManager = get(),
            serviceManager = get()
        )
    }
}

/**
 * Platform-specific DI module.
 *
 * Provides platform dependencies:
 * - Android: Context, PlatformGrantDelegate
 * - iOS: PlatformGrantDelegate
 */
expect val grantPlatformModule: Module
