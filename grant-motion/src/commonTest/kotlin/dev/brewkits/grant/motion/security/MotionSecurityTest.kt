package dev.brewkits.grant.motion.security

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.motion.GrantMotion
import dev.brewkits.grant.motion.fakes.FakeGrantManager
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Security tests for the Motion permission module.
 *
 * - SEC-M01: Callback cleared on dismiss (no memory leak)
 * - SEC-M02: PARTIAL_GRANTED is not a valid Motion status (all-or-nothing)
 * - SEC-M03: DENIED_ALWAYS never triggers system dialog
 * - SEC-M04: Multiple initialize() calls are idempotent
 * - SEC-M05: Repeated DENIED_ALWAYS requests never bypass block
 * - SEC-M06: Hardware-unavailable path does not invoke granted callback
 * - SEC-M07: Callback fires at most once per granted request
 * - SEC-M08: Status stable across repeated refreshStatus calls
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `SEC-M01 - callback cleared on dismiss and not invoked after late grant`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var invoked = false
        handler.request(settingsMessage = "Enable Motion") { invoked = true }
        advanceUntilIdle()

        handler.onDismiss()
        advanceUntilIdle()

        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertFalse(invoked, "Callback dismissed before grant — must not fire retroactively")
    }

    @Test
    fun `SEC-M02 - PARTIAL_GRANTED is not a valid Motion status`() = testScope.runTest {
        val validStatuses = listOf(
            GrantStatus.NOT_DETERMINED,
            GrantStatus.GRANTED,
            GrantStatus.DENIED,
            GrantStatus.DENIED_ALWAYS
        )

        for (status in validStatuses) {
            manager.mockStatus = status
            val handler = GrantHandler(manager, AppGrant.MOTION, testScope)
            handler.refreshStatus()
            advanceUntilIdle()
            assertNotEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value,
                "Motion must never return PARTIAL_GRANTED (hardware is all-or-nothing)")
        }
    }

    @Test
    fun `SEC-M03 - DENIED_ALWAYS never triggers system dialog request`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(5) {
            handler.request(settingsMessage = "Enable Motion in Settings") {}
            advanceUntilIdle()
        }

        val requestCount = manager.requestedGrants.count { it == AppGrant.MOTION }
        assertEquals(0, requestCount, "DENIED_ALWAYS must not trigger any system dialog")
    }

    @Test
    fun `SEC-M04 - multiple initialize calls are idempotent and do not corrupt state`() = testScope.runTest {
        repeat(10) { GrantMotion.initialize() }

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "Handler must work normally after repeated initialize() calls")
    }

    @Test
    fun `SEC-M05 - repeated DENIED_ALWAYS requests never bypass hardware block`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var grantedCalled = false
        repeat(20) {
            handler.request(settingsMessage = "Enable") { grantedCalled = true }
            advanceUntilIdle()
        }

        assertFalse(grantedCalled, "DENIED_ALWAYS must never invoke the granted callback")
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }

    @Test
    fun `SEC-M06 - hardware unavailable DENIED_ALWAYS does not invoke granted callback`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var grantedCalled = false
        handler.request(settingsMessage = "Motion hardware not available") { grantedCalled = true }
        advanceUntilIdle()

        assertFalse(grantedCalled,
            "Hardware-unavailable path must block granted callback — there is no hardware to grant access to")
        assertTrue(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `SEC-M07 - onGranted callback fires at most once per request cycle`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var callCount = 0
        handler.request { callCount++ }
        advanceUntilIdle()

        assertEquals(1, callCount, "onGranted must fire exactly once per request when already GRANTED")
    }

    @Test
    fun `SEC-M08 - status stable across repeated refreshStatus calls`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(20) {
            handler.refreshStatus()
            advanceUntilIdle()
            assertEquals(GrantStatus.GRANTED, handler.status.value,
                "GRANTED status must be stable across repeated refresh calls")
        }
    }

    @Test
    fun `SEC-M09 - handler state is never null`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)

        handler.refreshStatus()
        advanceUntilIdle()

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)
    }

    @Test
    fun `SEC-M10 - DENIED_ALWAYS state is preserved after failed requestSuspend attempt via request`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        handler.request(settingsMessage = "Enable motion") {}
        advanceUntilIdle()

        // DENIED_ALWAYS shows settings guide — status must remain DENIED_ALWAYS
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value,
            "Status must remain DENIED_ALWAYS after blocked request")
        assertFalse(handler.state.value.showRationale,
            "Rationale must NOT show for DENIED_ALWAYS — only settings guide")
    }
}
