package dev.brewkits.grant.motion

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.handlers.MotionPermissionHandler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * iOS-native tests for the Motion handler, exercised through the REAL handler the module
 * registers — not a fake.
 *
 * **Why this file exists.** `grant-motion` shipped 8 test files and ~40 assertions, all in
 * `commonTest`. On Android that source set runs against `GrantMotion.initialize()`, which is a
 * documented no-op — so every one of those tests passed without the iOS handler ever executing.
 * The CoreMotion mapping (`CMAuthorizationStatus` → `GrantStatus`, the `isActivityAvailable()`
 * guard, the simulator branch) had no coverage at all. That is the same gap that let a plist
 * defect sit unnoticed in `grant-calendar` until it was found by hand.
 *
 * Per the project's thread-safety convention, the suspend path is wrapped in [withTimeout] so a
 * deadlock in the CoreMotion callback surfaces as a failure instead of a hung runner —
 * `MotionPermissionHandler.request()` resumes from a native completion handler, which is
 * exactly where the `cont.isActive` guard documented on that class matters.
 *
 * The simulator has no motion hardware and no operator to answer a prompt, so these assert
 * "resolves to a valid status without throwing", not a specific value. Pinning a value here
 * would pin the simulator's behaviour, not the mapping's.
 */
class IosMotionHandlerTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED,
        GrantStatus.PARTIAL_GRANTED,
        GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS,
        GrantStatus.NOT_DETERMINED,
    )

    // The registry's get() is internal to grant-core, so the handler is constructed directly —
    // it is `internal` within this module, which is where these tests live. That still exercises
    // the real CoreMotion code path, which is the point; registration itself is covered by the
    // commonTest suite.
    private fun handler() = MotionPermissionHandler()

    @Test
    fun initialize_is_callable_and_idempotent() {
        GrantMotion.initialize()
        GrantMotion.initialize()
        assertNotNull(handler())
    }

    @Test
    fun checkStatus_resolves_to_a_valid_status_without_throwing() {
        val h = handler()
        val status = h.checkStatus()
        assertTrue(status in validStatuses, "checkStatus() returned $status")
    }

    @Test
    fun request_completes_without_deadlocking_on_the_CoreMotion_callback() = runTest(timeout = 20.seconds) {
        val h = handler()
        val status = withTimeout(15_000L) { h.request() }
        assertTrue(status in validStatuses, "request() returned $status")
    }

    /**
     * Calling `request()` twice must not throw. `MotionPermissionHandler` resumes its
     * continuation from a CoreMotion callback that `stopActivityUpdates()` cannot cancel, so a
     * second call arriving while the first callback is still in flight is precisely the case
     * the handler's `cont.isActive` guard exists for — an unguarded double-resume throws
     * `IllegalStateException`.
     */
    @Test
    fun repeated_requests_do_not_double_resume() = runTest(timeout = 30.seconds) {
        val h = handler()
        val first = withTimeout(12_000L) { h.request() }
        val second = withTimeout(12_000L) { h.request() }
        assertTrue(first in validStatuses && second in validStatuses)
    }
}
