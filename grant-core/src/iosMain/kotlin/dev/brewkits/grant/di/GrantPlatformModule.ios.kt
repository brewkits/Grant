package dev.brewkits.grant.di

import dev.brewkits.grant.impl.PlatformGrantDelegate
import org.koin.dsl.module

/**
 * iOS-specific DI module for grant management.
 *
 * Provides PlatformGrantDelegate that handles all iOS grant requests
 * using native iOS frameworks (AVFoundation, CoreLocation, etc.).
 */
actual val grantPlatformModule = module {
    single {
        PlatformGrantDelegate()
    }
}
