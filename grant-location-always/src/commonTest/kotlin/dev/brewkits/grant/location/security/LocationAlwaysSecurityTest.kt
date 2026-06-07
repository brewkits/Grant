package dev.brewkits.grant.location.security

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.location.GrantLocationAlways
import dev.brewkits.grant.location.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security / safety invariants for the LocationAlways module.
 *
 * - SEC-LA01: Callback cleared on dismiss (no retroactive invocation / leak)
 * - SEC-LA02: Always- and foreground-location status are strictly isolated
 * - SEC-LA03: Concurrent handlers cannot corrupt each other's callbacks
 * - SEC-LA04: Multiple initialize() calls are idempotent (no double-registration)
 * - SEC-LA05: DENIED_ALWAYS never triggers a system dialog (no privilege bypass)
 * - SEC-LA06: Repeated requests after DENIED_ALWAYS never grant
 * - SEC-LA07: onGranted fires at most once per request cycle
 * - SEC-LA08: Status stays consistent across repeated refresh
 * - SEC-LA09: Handler state/status are never null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationAlwaysSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `SEC-LA01 - callback cleared on dismiss and not invoked after late grant`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var invoked = false
        handler.request(settingsMessage = "Enable Always location") { invoked = true }
        advanceUntilIdle()

        handler.onDismiss()
        advanceUntilIdle()

        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertFalse(invoked, "Callback dismissed before grant — must not fire retroactively")
    }

    @Test
    fun `SEC-LA02 - always and foreground location status are fully isolated`() = testScope.runTest {
        manager.setStatus(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED_ALWAYS)
        manager.setStatus(AppGrant.LOCATION, GrantStatus.GRANTED)

        val always = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)
        val foreground = GrantHandler(manager, AppGrant.LOCATION, this)

        always.refreshStatus()
        foreground.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, always.status.value,
            "Always-location status must not be affected by foreground state")
        assertEquals(GrantStatus.GRANTED, foreground.status.value,
            "Foreground status must not be affected by always-location state")
    }

    @Test
    fun `SEC-LA03 - concurrent handlers do not corrupt each other's callbacks`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        var c1 = 0
        var c2 = 0
        val h1 = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)
        val h2 = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        repeat(10) {
            h1.request { c1++ }
            h2.request { c2++ }
        }
        advanceUntilIdle()

        assertTrue(c1 >= 1, "First handler must fire at least once")
        assertTrue(c2 >= 1, "Second handler must fire at least once")
    }

    @Test
    fun `SEC-LA04 - multiple initialize calls do not double-register or corrupt state`() = testScope.runTest {
        repeat(10) { GrantLocationAlways.initialize() }

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "Handler must work normally after repeated initialize() calls")
    }

    @Test
    fun `SEC-LA05 - DENIED_ALWAYS does not trigger a system dialog`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        repeat(5) {
            handler.request(settingsMessage = "Enable Always location in Settings") {}
            advanceUntilIdle()
        }

        val requestCount = manager.requestedGrants.count { it == AppGrant.LOCATION_ALWAYS }
        assertEquals(0, requestCount, "DENIED_ALWAYS must never trigger a system dialog request")
    }

    @Test
    fun `SEC-LA06 - repeated requests after DENIED_ALWAYS do not bypass block`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var grantedCalled = false
        repeat(20) {
            handler.request(settingsMessage = "Enable") { grantedCalled = true }
            advanceUntilIdle()
        }

        assertFalse(grantedCalled, "DENIED_ALWAYS must never invoke the granted callback")
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }

    @Test
    fun `SEC-LA07 - onGranted callback fires at most once per request cycle`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var callCount = 0
        handler.request { callCount++ }
        advanceUntilIdle()

        assertEquals(1, callCount, "onGranted must fire exactly once per request when already GRANTED")
    }

    @Test
    fun `SEC-LA08 - status remains consistent across refreshStatus calls`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        repeat(20) {
            handler.refreshStatus()
            advanceUntilIdle()
            assertEquals(GrantStatus.GRANTED, handler.status.value,
                "Status must remain stable across repeated refresh calls")
        }
    }

    @Test
    fun `SEC-LA09 - handler state and status are never null`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        assertNotNull(handler.state.value, "State must never be null")
        assertNotNull(handler.status.value, "Status must never be null")

        handler.refreshStatus()
        advanceUntilIdle()

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)
    }
}
