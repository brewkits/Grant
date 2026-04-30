package dev.brewkits.grant

/**
 * The operational state of a system service.
 */
enum class ServiceStatus {
    /**
     * The service is active and available for use.
     */
    ENABLED,

    /**
     * The service is currently disabled by the user or the system.
     *
     * On most platforms, this can be resolved by directing the user to Settings.
     */
    DISABLED,

    /**
     * The hardware required for this service is not present on this device.
     */
    NOT_AVAILABLE,

    /**
     * The status of the service could not be determined.
     */
    UNKNOWN
}
