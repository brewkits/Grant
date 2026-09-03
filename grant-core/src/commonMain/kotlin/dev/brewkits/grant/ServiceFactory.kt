package dev.brewkits.grant

import dev.brewkits.grant.impl.MyServiceManager
import dev.brewkits.grant.impl.PlatformServiceDelegate

/**
 * A static factory for creating [ServiceManager] instances.
 */
public expect object ServiceFactory {
    /**
     * Creates and returns a production-ready [ServiceManager] instance.
     */
    public fun createServiceManager(): ServiceManager
}
