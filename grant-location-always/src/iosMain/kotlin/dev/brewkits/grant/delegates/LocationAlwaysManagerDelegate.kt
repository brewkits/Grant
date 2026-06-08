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
internal class LocationAlwaysManagerDelegate : NSObject(), CLLocationManagerDelegateProtocol {

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
    @kotlin.concurrent.Volatile
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
     * Requests "always" (background + foreground) location authorization.
     * Maps to iOS NSLocationAlwaysAndWhenInUseUsageDescription.
     *
     * On iOS 13+, calling `requestAlwaysAuthorization()` directly on a
     * NotDetermined manager shows the system dialog with "Allow Once /
     * Allow While Using / Always Allow / Don't Allow" — no prior
     * `requestWhenInUseAuthorization()` call is needed.
     *
     * The previous pattern of calling both request methods synchronously
     * caused a flicker / instant-dismiss on iOS 11–12 because the WhenInUse
     * dialog is async and the Always call would fire before it resolved.
     * Grant targets iOS 13+ (minDeploymentTarget), so that workaround is
     * removed.
     */
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    suspend fun requestAlwaysAuthorization(): CLAuthorizationStatus {
        return suspendCancellableCoroutine { cont ->
            continuation = cont

            val manager = getManager()
            val currentStatus = manager.authorizationStatus()
            if (currentStatus == kCLAuthorizationStatusAuthorizedAlways) {
                cleanupAfterRequest()
                cont.resume(currentStatus)
                return@suspendCancellableCoroutine
            }

            manager.requestAlwaysAuthorization()

            cont.invokeOnCancellation {
                cleanupAfterRequest()
            }
        }
    }

    private fun cleanupAfterRequest() {
        continuation = null
        locationManager?.delegate = null
        locationManager = null
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
        // Null continuation before resuming so the legacy callback below is a safe no-op if it fires.
        val cont = continuation
        if (cont != null) {
            cleanupAfterRequest()
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
        // Already consumed by the iOS 14+ callback above → safe no-op.
        val cont = continuation ?: return
        cleanupAfterRequest()
        mainContinuation<CLAuthorizationStatus> { status ->
            cont.resume(status)
        }.invoke(didChangeAuthorizationStatus)
    }

    /**
     * Exposes the instance `authorizationStatus` property (iOS 14+ preferred API)
     * so that [LocationAlwaysPermissionHandler] does not need to call the deprecated
     * `CLLocationManager.authorizationStatus()` class method.
     */
    fun currentAuthorizationStatus(): CLAuthorizationStatus =
        locationManager?.authorizationStatus() ?: CLLocationManager().authorizationStatus()

    /**
     * Exposes the instance `accuracyAuthorization` property (iOS 14+).
     * Returns `CLAccuracyAuthorizationFullAccuracy` (0) or `CLAccuracyAuthorizationReducedAccuracy` (1).
     */
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    fun currentAccuracyAuthorization(): Long {
        val manager = locationManager ?: CLLocationManager()
        return if (manager.respondsToSelector(platform.Foundation.NSSelectorFromString("accuracyAuthorization"))) {
            manager.accuracyAuthorization.value.toLong()
        } else {
            0L // Full accuracy is default on iOS < 14
        }
    }

    /**
     * Requests temporary full accuracy authorization (iOS 14+).
     */
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    suspend fun requestTemporaryFullAccuracyAuthorization(purposeKey: String) {
        return suspendCancellableCoroutine { cont ->
            val manager = getManager()
            if (!manager.respondsToSelector(platform.Foundation.NSSelectorFromString("requestTemporaryFullAccuracyAuthorizationWithPurposeKey:completion:"))) {
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            
            manager.requestTemporaryFullAccuracyAuthorizationWithPurposeKey(purposeKey) { _ ->
                cont.resume(Unit)
            }
        }
    }
}
