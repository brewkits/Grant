package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import platform.CoreLocation.CLAuthorizationStatus
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
     * From iOS 14, assigning a delegate to a CLLocationManager triggers an immediate
     * `locationManagerDidChangeAuthorization` with the *current* status. The delegate sets
     * `continuation` first and assigns itself second, so that initial NotDetermined callback
     * would resume `requestWhenInUseAuthorization()` before the user has answered anything.
     *
     * Without a usage-description key CoreLocation shows no dialog, so a correct implementation
     * stays suspended: nobody has answered. Returning at all means it resumed spuriously.
     * (Only meaningful while authorisation is still NotDetermined on this simulator.)
     */
    @Test
    fun `R1 requestWhenInUse stays pending until the user answers`() {
        val locationDelegate = LocationManagerDelegate()
        if (locationDelegate.currentAuthorizationStatus() != kCLAuthorizationStatusNotDetermined) return

        var resumedWith: CLAuthorizationStatus? = null
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { resumedWith = locationDelegate.requestWhenInUseAuthorization() }

        // Spin the main run loop so CoreLocation's delegate callbacks can be delivered.
        val deadline = NSDate.dateWithTimeIntervalSinceNow(3.0)
        NSRunLoop.currentRunLoop.runUntilDate(deadline)

        val early = resumedWith
        locationDelegate.dispose()
        assertNull(
            early,
            "requestWhenInUseAuthorization() resumed with status $early although no one " +
                "answered a dialog — the initial delegate callback was treated as the answer",
        )
    }
}
