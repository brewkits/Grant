package dev.brewkits.grant.location

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.IosServiceRegistry
import dev.brewkits.grant.handlers.LocationPermissionHandler
import platform.CoreLocation.CLLocationManager

/**
 * iOS: registers the CoreLocation-backed handler for [AppGrant.LOCATION] (when in use) and the
 * Location Services check for [ServiceType.LOCATION_GPS].
 *
 * Background ("always") location is a separate module, `grant-location-always`.
 */
public actual object GrantLocation {
    private var isInitialized = false

    public actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(
            AppGrant.LOCATION.identifier,
            LocationPermissionHandler(delegate = LocationManagerDelegate()),
        )
        IosServiceRegistry.register(ServiceType.LOCATION_GPS, ::locationServicesStatus)
    }
}

internal fun locationServicesStatus(): ServiceStatus =
    if (CLLocationManager.locationServicesEnabled()) ServiceStatus.ENABLED else ServiceStatus.DISABLED
