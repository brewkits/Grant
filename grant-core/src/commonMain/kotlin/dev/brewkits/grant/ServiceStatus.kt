package dev.brewkits.grant

/**
 * Status of a system service.
 */
enum class ServiceStatus {
    /**
     * Service is enabled and ready to use
     */
    ENABLED,

    /**
     * Service is disabled by user (can be enabled in settings)
     */
    DISABLED,

    /**
     * Service is not available on this device
     */
    NOT_AVAILABLE,

    /**
     * Unable to determine service status
     */
    UNKNOWN
}
