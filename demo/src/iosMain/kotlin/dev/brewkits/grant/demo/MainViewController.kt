package dev.brewkits.grant.demo

import androidx.compose.ui.window.ComposeUIViewController
import dev.brewkits.grant.di.grantModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * iOS app entry point
 */
fun MainViewController(): UIViewController {
    // Initialize Koin for iOS
    startKoin {
        modules(
            grantModule,
            dev.brewkits.grant.di.grantPlatformModule
        )
    }

    return ComposeUIViewController {
        App()
    }
}
