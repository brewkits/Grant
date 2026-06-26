package dev.brewkits.grant.di

import android.content.Context
import dev.brewkits.grant.SharedPreferencesGrantStore
import dev.brewkits.grant.impl.PlatformGrantDelegate
import org.koin.dsl.module

/**
 * Android-specific DI module for grant management.
 *
 * Provides PlatformGrantDelegate that handles all Android grant requests
 * using Android ActivityResultContracts API.
 *
 * **Requirements**:
 * - Android Context must be provided by the app's DI setup
 */
actual val grantPlatformModule = module {
    single {
        val context = get<Context>()
        PlatformGrantDelegate(
            context = context,
            // Persistent store so request history survives process death (Issue #55).
            store = SharedPreferencesGrantStore(context)
        )
    }
}
