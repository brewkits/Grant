package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.PermissionHandler
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.Foundation.NSClassFromString
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Phase 1 reproduction tests for iOS (see the remediation plan).
 *
 * **Expected to FAIL on the current code** — each encodes the *correct* behaviour for a defect
 * found in review. A red test proves the defect is real; a test that passes means the review
 * finding was a false alarm and the item must be dropped from the plan.
 *
 * Not reproducible in a Simulator unit test (need a real device / app host — tracked separately):
 *  - D3 reduced-accuracy location mapping (`mapCLStatus` is private and needs a real
 *    CLLocationManager.accuracyAuthorization)
 *  - Notification request/check timeouts (need a live system dialog)
 *  - R13's end-to-end `openURL` call (no injection seam yet); only the suspect cast is tested.
 */
class Phase1IosReproTest {

    private val delegate = PlatformGrantDelegate(InMemoryGrantStore())

    // ── D1: an optional module that was never initialised must not fake "ask me later" ─────

    /**
     * `NotRegisteredHandler` answers NOT_DETERMINED to both check and request, with only a log
     * line. GrantHandler treats NOT_DETERMINED as "ask the user", so the app loops on a request
     * that can never show anything. The sibling web/desktop delegates answer DENIED_ALWAYS with
     * a logged reason for exactly this "platform cannot back this" case.
     */
    @Test
    fun `D1 unregistered optional module does not report NOT_DETERMINED`() = runTest {
        // Registry is process-global; only meaningful if no other test registered it.
        if (IosPermissionHandlerRegistry.get(AppGrant.APP_TRACKING.identifier) != null) return@runTest

        assertNotEquals(
            GrantStatus.NOT_DETERMINED,
            delegate.checkStatus(AppGrant.APP_TRACKING),
            "checkStatus for an unregistered module must not look like a first-time request",
        )
        assertNotEquals(
            GrantStatus.NOT_DETERMINED,
            delegate.request(AppGrant.APP_TRACKING),
            "request for an unregistered module can never show a dialog; NOT_DETERMINED here " +
                "sends GrantHandler into a request loop",
        )
    }

    // ── D2: a fabricated GRANTED ───────────────────────────────────────────────────────────

    /**
     * iOS has a real Local Network permission the user can deny. Grant cannot query it, so the
     * honest answer is "unknown" — not GRANTED, which this project forbids everywhere else
     * (web, desktop) for permissions the platform cannot back.
     */
    @Test
    fun `D2 LOCAL_NETWORK is not reported as GRANTED when iOS cannot be asked`() = runTest {
        assertNotEquals(
            GrantStatus.GRANTED,
            delegate.checkStatus(AppGrant.LOCAL_NETWORK),
            "iOS Local Network access can be denied; GRANTED is fabricated",
        )
    }

    // ── RawPermission without a handler ────────────────────────────────────────────────────

    /**
     * A RawPermission with no registered iOS handler can never be requested. request() logs and
     * returns NOT_DETERMINED without recording anything, so the status can never leave
     * NOT_DETERMINED and the `store.isRawPermissionRequested -> DENIED` branch in checkStatus is
     * unreachable on iOS.
     */
    @Test
    fun `RawPermission with no iOS handler does not stay NOT_DETERMINED after request`() = runTest {
        val raw = RawPermission(
            identifier = "phase1.unregistered.raw",
            androidPermissions = listOf("android.permission.CAMERA"),
            iosUsageKey = null,
        )
        assertNotEquals(
            GrantStatus.NOT_DETERMINED,
            delegate.request(raw),
            "request() cannot show anything for this permission; reporting NOT_DETERMINED " +
                "invites an endless retry",
        )
    }

    // ── R13: the KVC route to UIApplication ────────────────────────────────────────────────

    /**
     * `openSettings()` obtains UIApplication via
     * `(NSClassFromString("UIApplication") as? NSObject)?.valueForKey("sharedApplication")`.
     * If that cast yields null, Settings can never open and the only symptom is a log line.
     * This isolates just the suspect cast — no app host is needed for it.
     */
    @OptIn(kotlinx.cinterop.BetaInteropApi::class)
    @Test
    fun `R13 NSClassFromString UIApplication casts to NSObject so KVC can be used`() {
        assertNotNull(
            NSClassFromString("UIApplication") as? NSObject,
            "openSettings() relies on this cast; null means it silently never opens Settings",
        )
    }

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

    // ── R5 on iOS: checkStatus queues behind an unanswered request ─────────────────────────

    private class HangingHandler(private val gate: CompletableDeferred<Unit>) : PermissionHandler {
        override fun checkStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
        override suspend fun request(): GrantStatus {
            gate.await()
            return GrantStatus.GRANTED
        }
    }

    /**
     * Found on the same iPhone run that confirmed R1: once request() correctly waited for the user,
     * a status poll for the same permission produced no output at all, because checkStatus() takes
     * the per-permission mutex that the pending request() holds. (The R1 bug had hidden this by
     * returning from request() after 19 ms.) A UI refresh while the system dialog is open must not
     * hang. Same defect the Android delegate had.
     */
    @Test
    fun `R5 iOS checkStatus returns while a request for the same permission awaits the user`() {
        val id = "phase1.r5.ios.raw"
        val gate = CompletableDeferred<Unit>()
        IosPermissionHandlerRegistry.register(id, HangingHandler(gate))
        val raw = RawPermission(id, emptyList(), null)

        var observed: GrantStatus? = null
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { delegate.request(raw) }
        scope.launch {
            delay(300)
            observed = delegate.checkStatus(raw)
        }
        NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(3.0))

        val result = observed
        gate.complete(Unit) // let the pending request finish so nothing leaks into other tests
        NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.5))
        assertNotNull(result, "checkStatus() was still blocked after 3 s behind an unanswered request")
    }
}
