package dev.brewkits.grant.demo

import androidx.compose.ui.window.ComposeUIViewController
import dev.brewkits.grant.calendar.GrantCalendar
import dev.brewkits.grant.contacts.GrantContacts
import dev.brewkits.grant.bluetooth.GrantBluetooth
import dev.brewkits.grant.location.GrantLocationAlways
import dev.brewkits.grant.di.grantModule
import dev.brewkits.grant.motion.GrantMotion
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

/**
 * iOS app entry point.
 *
 * Optional permission modules must be initialized before their permissions can be
 * requested. Without these calls, CONTACTS/CALENDAR/MOTION return NOT_DETERMINED.
 */
fun MainViewController(): UIViewController {
    // Register optional permission handlers before Koin starts DI graph.
    GrantContacts.initialize()
    GrantCalendar.initialize()
    GrantMotion.initialize()
    GrantBluetooth.initialize()
    GrantLocationAlways.initialize()

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
