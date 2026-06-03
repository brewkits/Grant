package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.utils.hasInfoPlistKey
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.CLAccuracyAuthorization

/**
 * Handles requesting Temporary Full Accuracy Location permission on iOS 14+.
 * 
 * Maps to the `NSLocationTemporaryFullAccuracyUsageDescriptionKey` in Info.plist.
 * Since this requires a specific `purposeKey` matching your Info.plist dictionary,
 * it cannot be an `AppGrant` enum. Instead, use a `RawPermission` and register this handler:
 * 
 * ```kotlin
 * val precisionLoc = RawPermission("DeliveryPurpose", iosUsageKey = "NSLocationTemporaryFullAccuracyUsageDescriptionKey")
 * IosPermissionHandlerRegistry.register("DeliveryPurpose", LocationTemporaryFullAccuracyHandler("DeliveryPurpose"))
 * ```
 */
class LocationTemporaryFullAccuracyHandler(
    private val purposeKey: String
) : PermissionHandler {

    private val delegate: LocationManagerDelegate = LocationManagerDelegate()

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey("LocationTemporaryFullAccuracyHandler", "NSLocationTemporaryFullAccuracyUsageDescriptionKey")) {
            return GrantStatus.DENIED_ALWAYS
        }
        val clStatus = delegate.currentAuthorizationStatus()
        val accuracy = delegate.currentAccuracyAuthorization()

        if (clStatus != kCLAuthorizationStatusAuthorizedAlways && clStatus != kCLAuthorizationStatusAuthorizedWhenInUse) {
            return GrantStatus.DENIED_ALWAYS // Cannot get precision if we don't have location at all
        }

        // accuracy == CLAccuracyAuthorizationFullAccuracy (0)
        return if (accuracy == 0L) GrantStatus.GRANTED else GrantStatus.DENIED
    }

    override suspend fun request(): GrantStatus {
        if (checkStatus() == GrantStatus.GRANTED) return GrantStatus.GRANTED
        delegate.requestTemporaryFullAccuracyAuthorization(purposeKey)
        return checkStatus()
    }
}
