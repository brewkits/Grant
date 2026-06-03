package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted

private const val TAG = "LocationPermissionHandler"

/**
 * Handles Location permissions via CoreLocation framework.
 *
 * **Why CoreLocation is isolated here:**
 * Linking CoreLocation causes Apple to require NSLocationWhenInUseUsageDescription
 * and/or NSLocationAlwaysAndWhenInUseUsageDescription in Info.plist, even if the
 * app never requests location. Isolating imports to this file ensures the framework
 * is only linked when this handler is actually referenced by the app's code.
 *
 * Deprecated class method:
 * Previously called `CLLocationManager.authorizationStatus()` (class method), which
 * Apple deprecated in iOS 14 in favour of the instance property `authorizationStatus`
 * on a `CLLocationManager` instance. The fix delegates to `LocationManagerDelegate`
 * which already owns a `CLLocationManager` instance.
 *
 * @param forAlways  `true` → requests background ("always") authorization;
 *                   `false` → requests foreground ("when in use") authorization.
 * @param delegate   Shared [LocationManagerDelegate] that wraps the async CLLocationManager callbacks.
 */
internal class LocationPermissionHandler(
    private val forAlways: Boolean,
    private val delegate: LocationManagerDelegate
) : PermissionHandler {

    private val requiredPlistKey: String
        get() = if (forAlways) "NSLocationAlwaysAndWhenInUseUsageDescription"
                else "NSLocationWhenInUseUsageDescription"

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, requiredPlistKey)) return GrantStatus.DENIED_ALWAYS
        // Use instance authorizationStatus via the shared delegate rather than
        // the deprecated `CLLocationManager.authorizationStatus()` class method. (iOS 14+ deprecated).
        return mapCLStatus(delegate.currentAuthorizationStatus(), forAlways)
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, requiredPlistKey)) return GrantStatus.DENIED_ALWAYS
        val clStatus: CLAuthorizationStatus = if (forAlways) {
            delegate.requestAlwaysAuthorization()
        } else {
            delegate.requestWhenInUseAuthorization()
        }
        return mapCLStatus(clStatus, forAlways)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Internal helpers
// ────────────────────────────────────────────────────────────────────────────

private fun mapCLStatus(status: CLAuthorizationStatus, forAlways: Boolean): GrantStatus =
    when (status) {
        kCLAuthorizationStatusAuthorizedAlways    -> GrantStatus.GRANTED
        kCLAuthorizationStatusAuthorizedWhenInUse -> {
            if (forAlways) GrantStatus.PARTIAL_GRANTED else GrantStatus.GRANTED
        }
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted          -> GrantStatus.DENIED_ALWAYS
        kCLAuthorizationStatusNotDetermined       -> GrantStatus.NOT_DETERMINED
        else                                      -> GrantStatus.NOT_DETERMINED
    }
