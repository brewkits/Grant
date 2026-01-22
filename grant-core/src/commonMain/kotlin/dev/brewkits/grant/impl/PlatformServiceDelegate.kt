package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType

/**
 * Platform-specific service checking implementation.
 *
 * Each platform implements this differently:
 * - Android: Uses LocationManager, BluetoothAdapter, etc. (requires Context)
 * - iOS: Uses CLLocationManager, CBCentralManager, etc. (no dependencies)
 */
internal expect class PlatformServiceDelegate {
    suspend fun checkServiceStatus(service: ServiceType): ServiceStatus
    suspend fun openServiceSettings(service: ServiceType): Boolean
}
