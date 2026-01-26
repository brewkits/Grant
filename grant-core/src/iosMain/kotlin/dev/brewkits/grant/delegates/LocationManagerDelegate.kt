package dev.brewkits.grant.delegates

import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.*
import platform.darwin.NSObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Delegate for handling iOS CoreLocation grant requests.
 *
 * iOS location grants require a CLLocationManager and delegate to handle
 * asynchronous authorization changes. This class wraps that complexity into
 * a clean suspend function API.
 */
internal class LocationManagerDelegate : NSObject(), CLLocationManagerDelegateProtocol {

    private val locationManager = CLLocationManager()
    private var continuation: Continuation<CLAuthorizationStatus>? = null

    init {
        locationManager.delegate = this
    }

    /**
     * Requests "when in use" (foreground) location authorization.
     * Maps to iOS NSLocationWhenInUseUsageDescription.
     *
     * @return The resulting authorization status
     */
    suspend fun requestWhenInUseAuthorization(): CLAuthorizationStatus {
        return suspendCancellableCoroutine { cont ->
            continuation = cont

            // Check if already determined
            val currentStatus = locationManager.authorizationStatus()
            if (currentStatus != kCLAuthorizationStatusNotDetermined) {
                cont.resume(currentStatus)
                continuation = null
                return@suspendCancellableCoroutine
            }

            // Request authorization - delegate callback will resume continuation
            locationManager.requestWhenInUseAuthorization()

            cont.invokeOnCancellation {
                continuation = null
            }
        }
    }

    /**
     * Requests "always" (background + foreground) location authorization.
     * Maps to iOS NSLocationAlwaysUsageDescription.
     *
     * Note: On iOS 11+, you must first request WhenInUse before Always.
     * This function handles that automatically.
     *
     * @return The resulting authorization status
     */
    suspend fun requestAlwaysAuthorization(): CLAuthorizationStatus {
        return suspendCancellableCoroutine { cont ->
            continuation = cont

            // Check if already determined
            val currentStatus = locationManager.authorizationStatus()
            if (currentStatus == kCLAuthorizationStatusAuthorizedAlways) {
                cont.resume(currentStatus)
                continuation = null
                return@suspendCancellableCoroutine
            }

            // iOS 11+ requirement: Request WhenInUse first if not already granted
            if (currentStatus == kCLAuthorizationStatusNotDetermined ||
                currentStatus == kCLAuthorizationStatusDenied) {
                locationManager.requestWhenInUseAuthorization()
            }

            // Then request Always authorization
            locationManager.requestAlwaysAuthorization()

            cont.invokeOnCancellation {
                continuation = null
            }
        }
    }

    /**
     * CLLocationManagerDelegate callback when authorization status changes.
     * This is called after user responds to the grant dialog.
     */
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = manager.authorizationStatus()

        // Resume the waiting coroutine with the new status
        val cont = continuation
        if (cont != null) {
            mainContinuation<CLAuthorizationStatus> { s ->
                cont.resume(s)
            }.invoke(status)
            continuation = null
        }
    }

    /**
     * Legacy callback for iOS < 14 compatibility.
     */
    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {
        val cont = continuation
        if (cont != null) {
            mainContinuation<CLAuthorizationStatus> { status ->
                cont.resume(status)
            }.invoke(didChangeAuthorizationStatus)
            continuation = null
        }
    }
}
