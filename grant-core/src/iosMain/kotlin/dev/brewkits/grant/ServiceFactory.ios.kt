package dev.brewkits.grant

import dev.brewkits.grant.impl.MyServiceManager
import dev.brewkits.grant.impl.PlatformServiceDelegate

actual object ServiceFactory {
    actual fun createServiceManager(): ServiceManager {
        return MyServiceManager(
            platformDelegate = PlatformServiceDelegate()
        )
    }
}
