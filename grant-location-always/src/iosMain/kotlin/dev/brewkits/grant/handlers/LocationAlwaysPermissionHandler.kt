package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.LocationAlwaysManagerDelegate
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted

private const val TAG = "LocationAlwaysPermissionHandler"

/**
 * Handles LocationAlways permissions via CoreLocation framework.
 */
internal class LocationAlwaysPermissionHandler(
    private val delegate: LocationAlwaysManagerDelegate
) : PermissionHandler {

    private val requiredPlistKey: String
        get() = "NSLocationAlwaysAndWhenInUseUsageDescription"

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, requiredPlistKey)) return GrantStatus.DENIED_ALWAYS
        return mapCLStatus(delegate.currentAuthorizationStatus())
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, requiredPlistKey)) return GrantStatus.DENIED_ALWAYS
        val clStatus: CLAuthorizationStatus = delegate.requestAlwaysAuthorization()
        return mapCLStatus(clStatus)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Internal helpers
// ────────────────────────────────────────────────────────────────────────────

private fun mapCLStatus(status: CLAuthorizationStatus): GrantStatus =
    when (status) {
        kCLAuthorizationStatusAuthorizedAlways    -> GrantStatus.GRANTED
        kCLAuthorizationStatusAuthorizedWhenInUse -> GrantStatus.PARTIAL_GRANTED
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted          -> GrantStatus.DENIED_ALWAYS
        kCLAuthorizationStatusNotDetermined       -> GrantStatus.NOT_DETERMINED
        else                                      -> GrantStatus.NOT_DETERMINED
    }
