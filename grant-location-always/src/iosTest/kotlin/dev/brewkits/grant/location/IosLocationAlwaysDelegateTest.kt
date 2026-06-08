package dev.brewkits.grant.location

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.LocationAlwaysManagerDelegate
import dev.brewkits.grant.handlers.LocationAlwaysPermissionHandler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * iOS-native tests for the always-location delegate + handler, exercised through
 * the REAL [LocationAlwaysManagerDelegate] (not a fake).
 *
 * Per the project's thread-safety convention, the suspend `request()` path is
 * wrapped in [withTimeout] so a silent deadlock surfaces as a test failure.
 *
 * In the XCTest runner there is no `NSLocationAlwaysAndWhenInUseUsageDescription`
 * key, so the handler returns DENIED_ALWAYS BEFORE any system dialog is shown.
 * The deadlock (if any) would occur before that early return, making this a valid
 * guard while staying simulator-safe (no dialog, no hang).
 */
class IosLocationAlwaysDelegateTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED,
        GrantStatus.PARTIAL_GRANTED,
        GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS,
        GrantStatus.NOT_DETERMINED
    )

    private lateinit var delegate: LocationAlwaysManagerDelegate
    private lateinit var handler: LocationAlwaysPermissionHandler

    @BeforeTest
    fun setup() {
        delegate = LocationAlwaysManagerDelegate()
        handler = LocationAlwaysPermissionHandler(delegate)
    }

    @Test
    fun `delegate currentAuthorizationStatus does not crash`() {
        // Reading the authorization status must be a safe, side-effect-free call.
        // CLAuthorizationStatus is an Int enum (0..4); we only assert it returns a sane value.
        val raw = delegate.currentAuthorizationStatus()
        assertTrue(raw >= 0, "CLAuthorizationStatus should be a non-negative enum value, got $raw")
    }

    @Test
    fun `handler checkStatus returns a valid status`() {
        val status = handler.checkStatus()
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }

    @Test
    fun `handler request does not deadlock and returns a valid status`() = runTest {
        val status = withTimeout(10_000L) { handler.request() }
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }

    @Test
    fun `GrantLocationAlways initialize then handler request still completes`() = runTest {
        GrantLocationAlways.initialize()
        val status = withTimeout(10_000L) { handler.request() }
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }
}
