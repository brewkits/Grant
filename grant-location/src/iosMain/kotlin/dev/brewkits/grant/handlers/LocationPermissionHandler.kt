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

/** Raw `CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy`. */
private const val CL_ACCURACY_FULL = 0L

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
 * @param delegate   Shared [LocationManagerDelegate] that wraps the async CLLocationManager callbacks.
 */
internal class LocationPermissionHandler(
    private val delegate: LocationManagerDelegate
) : PermissionHandler {

    private val requiredPlistKey: String
        get() = "NSLocationWhenInUseUsageDescription"

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, requiredPlistKey)) return GrantStatus.DENIED_ALWAYS
        // Use instance authorizationStatus via the shared delegate rather than
        // the deprecated `CLLocationManager.authorizationStatus()` class method. (iOS 14+ deprecated).
        return mapLocationStatus(delegate.currentAuthorizationStatus(), delegate.currentAccuracyAuthorization())
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, requiredPlistKey)) return GrantStatus.DENIED_ALWAYS
        val clStatus: CLAuthorizationStatus = delegate.requestWhenInUseAuthorization()
        return mapLocationStatus(clStatus, delegate.currentAccuracyAuthorization())
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Internal helpers
// ────────────────────────────────────────────────────────────────────────────

/**
 * Maps CoreLocation's authorization to a [GrantStatus]. Internal (not private) so the mapping can
 * be unit-tested without a live CLLocationManager.
 *
 * @param accuracy raw `CLAccuracyAuthorization`: 0 = full accuracy, 1 = reduced (the user chose
 *   "Precise Location: Off"). Reduced is still usable location but not what a caller asking for
 *   LOCATION expects; Android reports the equivalent "Approximate" choice as PARTIAL_GRANTED.
 */
internal fun mapLocationStatus(status: CLAuthorizationStatus, accuracy: Long): GrantStatus =
    when (status) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse ->
            if (accuracy == CL_ACCURACY_FULL) GrantStatus.GRANTED else GrantStatus.PARTIAL_GRANTED
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted          -> GrantStatus.DENIED_ALWAYS
        kCLAuthorizationStatusNotDetermined       -> GrantStatus.NOT_DETERMINED
        else                                      -> GrantStatus.NOT_DETERMINED
    }
