package dev.brewkits.grant

/**
 * Manages the status of system-level services like GPS, Bluetooth, and Wi-Fi.
 *
 * A permission being [GrantStatus.GRANTED] does not guarantee that a feature
 * is functional. For example, if Location permission is granted but the GPS
 * hardware is disabled, location-based features will still fail.
 *
 * Use [ServiceManager] in combination with [GrantManager] or use the high-level
 * [GrantAndServiceChecker] to verify both software and hardware readiness.
 */
interface ServiceManager {
    /**
     * Checks the current availability and state of a system service.
     *
     * @param service The type of service to check (e.g., [ServiceType.LOCATION_GPS]).
     * @return The current [ServiceStatus].
     */
    suspend fun checkServiceStatus(service: ServiceType): ServiceStatus

    /**
     * A convenience check to see if a service is fully [ServiceStatus.ENABLED].
     *
     * @param service The type of service to check.
     * @return `true` if the service is [ServiceStatus.ENABLED], `false` otherwise.
     */
    suspend fun isServiceEnabled(service: ServiceType): Boolean {
        return checkServiceStatus(service) == ServiceStatus.ENABLED
    }

    /**
     * Directs the user to the system settings page to enable a specific service.
     *
     * - **Android**: Opens the specific service settings page (e.g., Location Settings).
     * - **iOS**: Opens the main Settings app (deep-linking to specific services is restricted).
     *
     * @param service The service the user needs to enable.
     * @return `true` if the settings page was opened successfully.
     */
    suspend fun openServiceSettings(service: ServiceType): Boolean
}
