package dev.brewkits.grant

/**
 * Manages system service status (GPS, Bluetooth, etc.).
 *
 * **Why is this needed?**
 * - Grant GRANTED ≠ Feature working
 * - User might disable service (GPS off, Bluetooth off)
 * - Better UX: Tell user exactly what's wrong
 *
 * **Example**:
 * ```kotlin
 * val locationGranted = GrantManager.checkStatus(GrantType.LOCATION) == GrantStatus.GRANTED
 * val gpsEnabled = serviceManager.isServiceEnabled(ServiceType.LOCATION_GPS)
 *
 * if (locationGranted && !gpsEnabled) {
 *     showEnableGPSDialog()
 * }
 * ```
 */
interface ServiceManager {
    /**
     * Check if a system service is enabled.
     *
     * @param service The service to check
     * @return Service status
     */
    suspend fun checkServiceStatus(service: ServiceType): ServiceStatus

    /**
     * Check if service is enabled (convenience method).
     *
     * @param service The service to check
     * @return true if ENABLED, false otherwise
     */
    suspend fun isServiceEnabled(service: ServiceType): Boolean {
        return checkServiceStatus(service) == ServiceStatus.ENABLED
    }

    /**
     * Open system settings to enable a service.
     *
     * - Android: Opens specific settings (Location, Bluetooth, etc.)
     * - iOS: Opens Settings app (cannot deep-link to specific service)
     *
     * @param service The service to enable
     * @return true if settings opened successfully
     */
    suspend fun openServiceSettings(service: ServiceType): Boolean
}
