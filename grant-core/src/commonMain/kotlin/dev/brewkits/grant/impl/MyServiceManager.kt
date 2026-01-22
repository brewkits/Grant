package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceManager
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType

/**
 * Default implementation of ServiceManager using platform delegates.
 *
 * This class delegates to platform-specific implementations while
 * providing common logic and caching.
 */
class MyServiceManager internal constructor(
    private val platformDelegate: PlatformServiceDelegate
) : ServiceManager {

    override suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        return try {
            platformDelegate.checkServiceStatus(service)
        } catch (e: Exception) {
            // If check fails, return UNKNOWN instead of crashing
            ServiceStatus.UNKNOWN
        }
    }

    override suspend fun openServiceSettings(service: ServiceType): Boolean {
        return try {
            platformDelegate.openServiceSettings(service)
        } catch (e: Exception) {
            false
        }
    }
}
