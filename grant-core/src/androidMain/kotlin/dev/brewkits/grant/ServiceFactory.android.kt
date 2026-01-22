package dev.brewkits.grant

import android.content.Context
import dev.brewkits.grant.impl.MyServiceManager
import dev.brewkits.grant.impl.PlatformServiceDelegate

actual object ServiceFactory {
    private lateinit var applicationContext: Context

    /**
     * Initialize with Android application context.
     * Call this in Application.onCreate()
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    actual fun createServiceManager(): ServiceManager {
        if (!::applicationContext.isInitialized) {
            throw IllegalStateException(
                "ServiceFactory not initialized. Call ServiceFactory.init(context) in Application.onCreate()"
            )
        }

        return MyServiceManager(
            platformDelegate = PlatformServiceDelegate(applicationContext)
        )
    }
}
