package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.utils.GrantLogger

private const val TAG = "JvmServiceDelegate"

/**
 * `grant-core`'s own `jvmMain` has no hardware/OS-service signal to read on any target JVM —
 * that requires `grant-desktop` (Tier 2.5's Windows registry read for camera/mic is a service
 * check, not a permission, and is planned for a future release of that module; see
 * ROADMAP.md v2.6.0). Reports [ServiceStatus.UNKNOWN] for everything, which
 * [dev.brewkits.grant.impl.MyServiceManager] already treats as its safe fallback on every
 * platform.
 */
internal actual class PlatformServiceDelegate {

    actual suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        GrantLogger.d(TAG, "ServiceType.$service has no signal without the grant-desktop module; reporting UNKNOWN.")
        return ServiceStatus.UNKNOWN
    }

    actual suspend fun openServiceSettings(service: ServiceType): Boolean {
        GrantLogger.w(TAG, "openServiceSettings() has no effect without the grant-desktop module for $service.")
        return false
    }
}
