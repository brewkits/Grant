package dev.brewkits.grant

import dev.brewkits.grant.impl.MyServiceManager
import dev.brewkits.grant.impl.PlatformServiceDelegate

/**
 * Factory for creating ServiceManager instances.
 *
 * Platform-specific implementation.
 */
expect object ServiceFactory {
    fun createServiceManager(): ServiceManager
}
