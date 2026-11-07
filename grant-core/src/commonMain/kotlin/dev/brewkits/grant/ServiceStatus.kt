package dev.brewkits.grant

/**
 * The operational state of a system service.
 */
public enum class ServiceStatus {
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
     *
     * **Not the same as ready.** [GrantAndServiceChecker] and `ServiceManager.isServiceEnabled`
     * treat anything other than [ENABLED] as "not confirmed", so a feature gated on them stays
     * blocked while the state is unknown. That is deliberate: acting on a service that may be off
     * is the failure these checks exist to prevent.
     *
     * Because of that, a platform that cannot read a service at all chooses between blocking every
     * flow (report [UNKNOWN]) and not blocking (report [ENABLED]). iOS reports [ENABLED] for
     * [ServiceType.WIFI] for this reason; see that entry.
     */
    UNKNOWN
}
