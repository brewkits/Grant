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
 *
 * **FIX C3 — Double-resume prevention:**
 * On iOS 14+, both `locationManagerDidChangeAuthorization` (new) and
 * `locationManager(_:didChangeAuthorizationStatus:)` (legacy) are called.
 * The legacy callback was NOT deprecated at runtime, so both fire on iOS 14+.
 *
 * Fix: `continuation` is set to `null` BEFORE calling `.resume()`.
 * The second callback then sees `continuation == null` and returns early,
 * preventing the "resumed continuation twice" crash (EXC_BAD_ACCESS /
 * IllegalStateException on Kotlin/Native).
 */
internal class LocationManagerDelegate : NSObject(), CLLocationManagerDelegateProtocol {

    private var locationManager: CLLocationManager? = null

    private fun getManager(): CLLocationManager {
        val manager = locationManager ?: CLLocationManager()
        if (manager.delegate == null) manager.delegate = this
        locationManager = manager
        return manager
    }

    /**
     * Active continuation — nulled out BEFORE resume to prevent double-resume.
     * Pattern: val cont = continuation; continuation = null; cont?.resume(...)
     */
    private var continuation: Continuation<CLAuthorizationStatus>? = null



    /**
     * Call when done to break the retain cycle.
     */
    fun dispose() {
        locationManager?.delegate = null
        locationManager = null
        continuation = null
    }

    /**
     * Requests "when in use" (foreground) location authorization.
     * Maps to iOS NSLocationWhenInUseUsageDescription.
     */
    suspend fun requestWhenInUseAuthorization(): CLAuthorizationStatus {
        return suspendCancellableCoroutine { cont ->
            continuation = cont

            // If already determined, resume immediately without showing dialog
            val manager = getManager()
            val currentStatus = manager.authorizationStatus()
            if (currentStatus != kCLAuthorizationStatusNotDetermined) {
                continuation = null
                manager.delegate = null
                locationManager = null
                cont.resume(currentStatus)
                return@suspendCancellableCoroutine
            }

            manager.requestWhenInUseAuthorization()

            cont.invokeOnCancellation {
                continuation = null
                locationManager?.delegate = null
                locationManager = null
            }
        }
    }

    /**
     * Requests "always" (background + foreground) location authorization.
     * Maps to iOS NSLocationAlwaysAndWhenInUseUsageDescription.
     *
     * Note: On iOS 11+, you must first request WhenInUse before Always.
     * This function handles that automatically.
     */
    suspend fun requestAlwaysAuthorization(): CLAuthorizationStatus {
        return suspendCancellableCoroutine { cont ->
            continuation = cont

            val manager = getManager()
            val currentStatus = manager.authorizationStatus()
            if (currentStatus == kCLAuthorizationStatusAuthorizedAlways) {
                continuation = null
                manager.delegate = null
                locationManager = null
                cont.resume(currentStatus)
                return@suspendCancellableCoroutine
            }

            // iOS 11+ requirement: request WhenInUse first if not already granted
            if (currentStatus == kCLAuthorizationStatusNotDetermined ||
                currentStatus == kCLAuthorizationStatusDenied) {
                manager.requestWhenInUseAuthorization()
            }

            manager.requestAlwaysAuthorization()

            cont.invokeOnCancellation {
                continuation = null
                locationManager?.delegate = null
                locationManager = null
            }
        }
    }

    /**
     * iOS 14+ callback: `locationManagerDidChangeAuthorization`.
     *
     * FIX C3: Capture and null `continuation` BEFORE calling resume.
     * This means if the legacy callback fires afterwards on iOS 14+,
     * it sees `continuation == null` and is a safe no-op.
     */
    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val status = manager.authorizationStatus()
        // FIX C3: null continuation FIRST to block the legacy callback
        val cont = continuation
        continuation = null
        if (cont != null) {
            manager.delegate = null
            if (locationManager == manager) {
                locationManager = null
            }
            mainContinuation<CLAuthorizationStatus> { s -> cont.resume(s) }.invoke(status)
        }
    }

    /**
     * Legacy callback for iOS < 14.
     * On iOS 14+, this fires AFTER `locationManagerDidChangeAuthorization`.
     *
     * FIX C3: Guard on `continuation == null` (already consumed above) → safe no-op.
     */
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun locationManager(
        manager: CLLocationManager,
        didChangeAuthorizationStatus: CLAuthorizationStatus
    ) {
        // FIX C3: If already consumed by the iOS 14+ callback above → skip
        val cont = continuation ?: return
        continuation = null
        manager.delegate = null
        locationManager = null
        mainContinuation<CLAuthorizationStatus> { status ->
            cont.resume(status)
        }.invoke(didChangeAuthorizationStatus)
    }

    /**
     * Exposes the instance `authorizationStatus` property (iOS 14+ preferred API)
     * so that [LocationPermissionHandler] does not need to call the deprecated
     * `CLLocationManager.authorizationStatus()` class method.
     */
    fun currentAuthorizationStatus(): CLAuthorizationStatus {
        val manager = CLLocationManager() // Ephemeral for check
        return manager.authorizationStatus()
    }
}
