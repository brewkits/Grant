package dev.brewkits.grant

import dev.brewkits.grant.impl.MyServiceManager
import dev.brewkits.grant.impl.PlatformServiceDelegate

public actual object ServiceFactory {
    public actual fun createServiceManager(): ServiceManager {
        return MyServiceManager(
            platformDelegate = PlatformServiceDelegate()
        )
    }
}
