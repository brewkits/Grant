package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.mainContinuation
import dev.brewkits.grant.utils.SimulatorDetector
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import kotlin.coroutines.resume

private const val TAG = "MotionPermissionHandler"

/**
 * Handles Motion & Fitness permissions via CoreMotion framework.
 *
 * **Why CoreMotion is isolated here:**
 * Linking CoreMotion causes Apple to require NSMotionUsageDescription in Info.plist,
 * even if the app never requests motion access. Isolating this import prevents that.
 *
 * **Dummy query pattern:**
 * CoreMotion does not expose a direct `requestAuthorization()` API. The standard
 * approach (used by moko-permissions and flutter permission_handler) is to initiate
 * a short activity query, which triggers the system permission dialog as a side effect.
 *
 * Cancellation safety:
 * `stopActivityUpdates()` prevents future updates but does NOT cancel an already-dispatched
 * query callback. The callback can still fire after cancellation and call `cont.resume()`
 * on a completed coroutine, throwing `IllegalStateException`. The fix guards with
 * `cont.isActive` before every resume.
 */
internal class MotionPermissionHandler : PermissionHandler {

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, "NSMotionUsageDescription")) return GrantStatus.DENIED_ALWAYS
        if (SimulatorDetector.isSimulator && SimulatorDetector.mockSimulatorGranted) {
            GrantLogger.i(TAG, "Simulator detected — returning GRANTED for Motion checkStatus.")
            return GrantStatus.GRANTED
        }
        if (!CMMotionActivityManager.isActivityAvailable()) {
            GrantLogger.w(TAG, "Motion activity hardware not available on this device.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (CMMotionActivityManager.authorizationStatus()) {
            CMAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
            CMAuthorizationStatusDenied,
            CMAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
            CMAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else                               -> GrantStatus.NOT_DETERMINED
        }
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, "NSMotionUsageDescription")) return GrantStatus.DENIED_ALWAYS
        if (SimulatorDetector.isSimulator && SimulatorDetector.mockSimulatorGranted) {
            GrantLogger.i(TAG, "Simulator detected — returning GRANTED for Motion request.")
            return GrantStatus.GRANTED
        }
        if (!CMMotionActivityManager.isActivityAvailable()) return GrantStatus.DENIED_ALWAYS

        return suspendCancellableCoroutine { cont ->
            val activityManager = CMMotionActivityManager()
            val now = NSDate()

            cont.invokeOnCancellation {
                // Prevents future callbacks, but an already-dispatched callback may still fire.
                // The cont.isActive guard below handles that race.
                activityManager.stopActivityUpdates()
                GrantLogger.i(TAG, "Motion dummy query cancelled — activityManager stopped.")
            }

            activityManager.queryActivityStartingFromDate(
                start = now,
                toDate = now,
                toQueue = NSOperationQueue.mainQueue
            ) { _, _ ->
                val result = when (CMMotionActivityManager.authorizationStatus()) {
                    CMAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
                    CMAuthorizationStatusDenied,
                    CMAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
                    CMAuthorizationStatusNotDetermined -> GrantStatus.DENIED
                    else                               -> GrantStatus.DENIED
                }
                // Guard before resume — stopActivityUpdates() does not cancel an
                // ongoing callback dispatch in some CoreMotion race conditions.
                // Without this guard, resuming a cancelled coroutine throws IllegalStateException.
                mainContinuation<GrantStatus> { s ->
                    if (cont.isActive) cont.resume(s)
                }.invoke(result)
            }
        }
    }
}
