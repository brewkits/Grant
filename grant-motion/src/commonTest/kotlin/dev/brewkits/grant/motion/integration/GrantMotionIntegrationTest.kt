package dev.brewkits.grant.motion.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
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
import kotlin.test.assertTrue

/**
 * Integration tests for MOTION permission through GrantHandler.
 *
 * Motion is unique compared to other optional permissions:
 * - Single permission (no read-only variant)
 * - iOS: hardware availability check (CMMotionActivityManager.isActivityAvailable)
 *   — unavailable hardware → DENIED_ALWAYS before showing any dialog
 * - Android: ACTIVITY_RECOGNITION on API 29+, GMS fallback for older APIs
 * - No PARTIAL_GRANTED state (all-or-nothing)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantMotionIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── checkStatus ────────────────────────────────────────────────────────

    @Test
    fun `checkStatus MOTION returns valid GrantStatus`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = motionHandler()
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    // ── happy path ─────────────────────────────────────────────────────────

    @Test
    fun `request MOTION NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = motionHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertTrue(manager.requestCalled)
        assertEquals(AppGrant.MOTION, manager.requestedGrants.first())
    }

    @Test
    fun `request MOTION when already GRANTED does not re-request`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = motionHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertFalse(manager.requestCalled)
    }

    // ── hardware unavailable (DENIED_ALWAYS without dialog) ───────────────

    @Test
    fun `MOTION hardware unavailable returns DENIED_ALWAYS immediately`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.MOTION,
            scope = testScope
        )
        handler.request(settingsMessage = "Motion activity unavailable on this device") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide,
                "Hardware unavailable should show settings guide")
        }
    }

    // ── denial flows ───────────────────────────────────────────────────────

    @Test
    fun `MOTION DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.MOTION,
            scope = testScope
        )
        handler.request(settingsMessage = "Enable Motion in Settings > Privacy") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide)
            assertFalse(state.showRationale)
        }
    }

    @Test
    fun `MOTION DENIED does not permanently block subsequent request`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = motionHandler()
        handler.request {}
        advanceUntilIdle()

        manager.configure(AppGrant.MOTION, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
    }

    // ── requestSuspend ─────────────────────────────────────────────────────

    @Test
    fun `requestSuspend MOTION returns status directly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = motionHandler()
        val result = handler.requestSuspend()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, result)
    }

    // ── helper ─────────────────────────────────────────────────────────────

    private fun motionHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.MOTION,
        scope = testScope
    )
}
