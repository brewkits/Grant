package dev.brewkits.grant.location

/**
 * Entry point of the `grant-location` module, which provides the when-in-use location handler (`AppGrant.LOCATION`) and the location-services check (`ServiceType.LOCATION_GPS`).
 *
 * On iOS these live outside `grant-core` so that an app which never uses them does not link
 * `CoreLocation.framework`, and App Store Connect does not ask it for NSLocationWhenInUseUsageDescription (Issue #91).
 *
 * Call [initialize] once at startup, before the first request, on every platform. It is safe
 * to call from common code and to call more than once.
 */
public expect object GrantLocation {
    /**
     * Registers this module's handlers with `grant-core`. Required on iOS. A no-op on the other
     * platforms, where `grant-core` handles these permissions itself.
     */
    public fun initialize()
}
