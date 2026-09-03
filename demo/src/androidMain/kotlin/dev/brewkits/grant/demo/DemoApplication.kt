package dev.brewkits.grant.demo

import android.app.Application
import android.util.Log
import dev.brewkits.grant.di.grantModule
import dev.brewkits.grant.di.grantPlatformModule
import dev.brewkits.grant.utils.GrantLogger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DemoApplication : Application() {
    companion object {
        var instance: DemoApplication? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        DemoLogging.enableGrantLogging()
        // GrantLogger's built-in console branch is a plain println, which Android only routes
        // to Logcat on userdebug/eng system images — never on a retail user-build device
        // (verified on a physical Android 17 phone: ro.build.type=user, ro.debuggable=0).
        // Installing a logHandler that calls android.util.Log bypasses println entirely, so
        // the demo's Grant diagnostics are visible in Logcat on every device, not just
        // emulators. See GrantLogger's KDoc for the general guidance this follows.
        GrantLogger.logHandler = { _, tag, message -> Log.d(tag, message) }

        startKoin {
            androidLogger()
            androidContext(this@DemoApplication)
            modules(
                grantModule,
                grantPlatformModule
            )
        }
    }
}
