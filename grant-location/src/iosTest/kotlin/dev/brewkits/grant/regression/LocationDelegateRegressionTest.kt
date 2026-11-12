package dev.brewkits.grant.regression

import dev.brewkits.grant.delegates.LocationManagerDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Regression tests for [LocationManagerDelegate]. Moved here from grant-core's
 * Phase1IosReproTest together with the delegate in 3.0.0 (Issue #91).
 */
class LocationDelegateRegressionTest {

    // ── R1: CoreLocation's initial delegate callback ───────────────────────────────────────

    /**
     * R1 — CONFIRMED on a physical iPhone 14 Pro Max (iOS 26.6.2): with the old code
     * `request(LOCATION)` returned NOT_DETERMINED after 19 ms, before anyone could answer the
     * dialog, because iOS 14+ delivers `locationManagerDidChangeAuthorization` as soon as a
     * delegate is assigned, reporting the *current* (NotDetermined) status, and the delegate
     * treated that as the answer. (It did not reproduce on the Simulator, which is why this test
     * could not catch it; the device probe did.)
     *
     * Both halves of the fixed contract are checked, whichever the host environment is:
     *  - Location Services OFF: no dialog can ever appear, so the request must return promptly
     *    rather than hang.
     *  - Location Services ON: the request must stay pending until someone answers.
     * Only meaningful while authorisation is still NotDetermined on this simulator.
     */
    @Test
    fun `R1 requestWhenInUse waits for a real answer and never hangs without a dialog`() {
        val locationDelegate = LocationManagerDelegate()
        if (locationDelegate.currentAuthorizationStatus() != kCLAuthorizationStatusNotDetermined) return

        var resumedWith: CLAuthorizationStatus? = null
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { resumedWith = locationDelegate.requestWhenInUseAuthorization() }

        // Spin the main run loop so CoreLocation's delegate callbacks can be delivered.
        NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(3.0))

        val result = resumedWith
        val servicesOn = CLLocationManager.locationServicesEnabled()
        locationDelegate.dispose()
        if (servicesOn) {
            assertNull(result, "resumed with status $result although no one answered a dialog")
        } else {
            assertNotNull(result, "Location Services are off, so no dialog will ever appear; request() must not hang")
        }
    }
}
