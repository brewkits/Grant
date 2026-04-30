package dev.brewkits.grant

import dev.brewkits.grant.impl.MyServiceManager
import dev.brewkits.grant.impl.PlatformServiceDelegate

/**
 * A static factory for creating [ServiceManager] instances.
 */
expect object ServiceFactory {
    /**
     * Creates and returns a production-ready [ServiceManager] instance.
     */
    fun createServiceManager(): ServiceManager
}
