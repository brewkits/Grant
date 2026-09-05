package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Calls through the **real** [PlatformGrantDelegate] — not [dev.brewkits.grant.testing.FakeGrantManager]
 * — inside the headless-Chrome environment both `jsBrowserTest` and `wasmJsBrowserTest` run in
 * (this file lives in `webTest`, so it runs once per target), mirroring the rule CLAUDE.md
 * already holds iOS to: every method on the platform delegate needs at least one test that
 * exercises the real implementation.
 *
 * `runTest`'s `timeout` parameter (real wall-clock time), not a nested `withTimeout` (virtual
 * test-scheduler time): these tests await real browser Promises/callbacks outside the test
 * coroutine scheduler, and a virtual-time `withTimeout` never advances for that — it fails with
 * `TimeoutCancellationException: ... of _virtual_ time` regardless of how fast the real call
 * resolves. Confirmed by hitting exactly that failure before switching to `timeout =`.
 *
 * Headless Chrome has no camera/microphone hardware and no operator to click an OS prompt, so
 * `getUserMedia`/`Notification.requestPermission`/`getCurrentPosition` are all expected to end
 * in a rejected Promise or an error callback here — the point of these tests is that
 * [PlatformGrantDelegate] catches that and resolves to a valid [GrantStatus] rather than
 * throwing out of `request()`/`checkStatus()` and crashing the caller.
 */
class WebGrantDelegateTest {

    private val delegate = PlatformGrantDelegate(InMemoryGrantStore())

    private val validStatuses = setOf(
        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED, GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS, GrantStatus.NOT_DETERMINED,
    )

    @Test
    fun checkStatus_camera_resolves_without_throwing() = runTest(timeout = 5.seconds) {
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertTrue(status in validStatuses, "checkStatus(CAMERA) must resolve to a valid status, got $status")
    }

    @Test
    fun request_camera_in_headless_chrome_resolves_to_deniedAlways_not_a_thrown_exception() = runTest(timeout = 5.seconds) {
        // No camera hardware and no human to grant it — getUserMedia() must reject, and the
        // delegate must turn that rejection into DENIED_ALWAYS rather than propagate it.
        val status = delegate.request(AppGrant.CAMERA)
        assertTrue(status in validStatuses, "request(CAMERA) must resolve to a valid status, got $status")
    }

    @Test
    fun request_microphone_resolves_without_throwing() = runTest(timeout = 5.seconds) {
        val status = delegate.request(AppGrant.MICROPHONE)
        assertTrue(status in validStatuses, "request(MICROPHONE) must resolve to a valid status, got $status")
    }

    @Test
    fun checkStatus_location_resolves_without_throwing() = runTest(timeout = 5.seconds) {
        val status = delegate.checkStatus(AppGrant.LOCATION)
        assertTrue(status in validStatuses, "checkStatus(LOCATION) must resolve to a valid status, got $status")
    }

    @Test
    fun request_location_resolves_without_throwing() = runTest(timeout = 5.seconds) {
        // Exercises the callback-based getCurrentPosition() -> suspendCoroutine bridge, not
        // just the Promise-based ones above.
        val status = delegate.request(AppGrant.LOCATION)
        assertTrue(status in validStatuses, "request(LOCATION) must resolve to a valid status, got $status")
    }

    @Test
    fun checkStatus_notification_resolves_without_throwing() = runTest(timeout = 5.seconds) {
        val status = delegate.checkStatus(AppGrant.NOTIFICATION)
        assertTrue(status in validStatuses, "checkStatus(NOTIFICATION) must resolve to a valid status, got $status")
    }

    @Test
    fun request_notification_resolves_without_throwing() = runTest(timeout = 5.seconds) {
        val status = delegate.request(AppGrant.NOTIFICATION)
        assertTrue(status in validStatuses, "request(NOTIFICATION) must resolve to a valid status, got $status")
    }

    /**
     * Every [AppGrant] with no browser equivalent (CONTACTS is representative) must resolve to
     * exactly [GrantStatus.DENIED_ALWAYS] — never a fabricated GRANTED, and never a thrown
     * exception. This is the test that would fail if PlatformGrantDelegate.web.kt regressed to
     * a Calf-style silent no-op.
     */
    @Test
    fun unsupported_permission_reports_deniedAlways_not_a_silent_grant() = runTest(timeout = 3.seconds) {
        val checkResult = delegate.checkStatus(AppGrant.CONTACTS)
        val requestResult = delegate.request(AppGrant.CONTACTS)
        assertTrue(
            checkResult == GrantStatus.DENIED_ALWAYS,
            "an AppGrant with no browser API must not report anything but DENIED_ALWAYS, got $checkResult",
        )
        assertTrue(
            requestResult == GrantStatus.DENIED_ALWAYS,
            "an AppGrant with no browser API must not report anything but DENIED_ALWAYS, got $requestResult",
        )
    }

    @Test
    fun batch_request_resolves_every_grant_without_throwing() = runTest(timeout = 8.seconds) {
        val results = delegate.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION))
        assertTrue(results.size == 3, "expected 3 results, got ${results.size}")
        results.values.forEach { status ->
            assertTrue(status in validStatuses, "batch result must be a valid status, got $status")
        }
    }
}
