package dev.brewkits.grant.handlers

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType

/**
 * Service-side counterpart of [IosPermissionHandlerRegistry]: lets an opt-in module supply the
 * iOS check for a [ServiceType] whose native API `grant-core` must not link.
 *
 * It exists for [ServiceType.LOCATION_GPS]. Reading whether Location Services are on needs
 * `CLLocationManager`, and any reference to it from `grant-core` makes every consuming app link
 * CoreLocation, including apps that never use location (Issue #91). `grant-location` registers
 * that check from `GrantLocation.initialize()`. Without it, `LOCATION_GPS` reports
 * [ServiceStatus.UNKNOWN].
 *
 * Checkers run on the main thread and must not block.
 */
public object IosServiceRegistry {
    private val checkers = mutableMapOf<ServiceType, () -> ServiceStatus>()

    /** Registers the status check for [service], replacing any earlier one. */
    public fun register(service: ServiceType, checker: () -> ServiceStatus) {
        checkers[service] = checker
    }

    internal fun get(service: ServiceType): (() -> ServiceStatus)? = checkers[service]

    /** Test-only: the registry is process-global, so tests that register must clean up. */
    internal fun unregister(service: ServiceType) {
        checkers.remove(service)
    }
}
