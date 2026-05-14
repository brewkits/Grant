package dev.brewkits.grant.calendar.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.calendar.fakes.FakeGrantManager
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
 * Integration tests for CALENDAR and READ_CALENDAR permissions.
 *
 * Key scenarios:
 * - Basic GRANTED / DENIED_ALWAYS flows
 * - PARTIAL_GRANTED (iOS 17+ write-only access) state machine
 * - Least-privilege: READ_CALENDAR vs CALENDAR
 * - CALENDAR and READ_CALENDAR are independent permission handlers
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantCalendarIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── checkStatus ────────────────────────────────────────────────────────

    @Test
    fun `checkStatus CALENDAR returns valid GrantStatus`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = calendarHandler()
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `checkStatus READ_CALENDAR returns valid GrantStatus independently`() = testScope.runTest {
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.GRANTED)
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED)
        val readHandler = readCalendarHandler()
        val fullHandler = calendarHandler()
        readHandler.refreshStatus()
        fullHandler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, readHandler.status.value)
        assertEquals(GrantStatus.NOT_DETERMINED, fullHandler.status.value)
    }

    // ── request happy path ─────────────────────────────────────────────────

    @Test
    fun `request CALENDAR NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = calendarHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertTrue(manager.requestCalled)
    }

    @Test
    fun `request READ_CALENDAR NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = readCalendarHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertEquals(AppGrant.READ_CALENDAR, manager.requestedGrants.first())
    }

    @Test
    fun `request CALENDAR when already GRANTED does not re-request`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = calendarHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertFalse(manager.requestCalled, "No request should be sent when already GRANTED")
    }

    // ── PARTIAL_GRANTED (iOS 17+ write-only) ───────────────────────────────

    @Test
    fun `request CALENDAR returns PARTIAL_GRANTED for iOS 17 write-only access`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.PARTIAL_GRANTED)
        val handler = calendarHandler()
        var partialCalled = false
        handler.request { partialCalled = false }
        advanceUntilIdle()
        // PARTIAL_GRANTED should NOT invoke the onGranted callback (only GRANTED does)
        assertFalse(partialCalled)
        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    @Test
    fun `PARTIAL_GRANTED CALENDAR completes flow with PARTIAL_GRANTED status`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.PARTIAL_GRANTED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CALENDAR, scope = testScope)
        var receivedStatus: GrantStatus? = null
        handler.request(settingsMessage = "Enable full Calendar access in Settings") { status ->
            receivedStatus = status
        }
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            // PARTIAL_GRANTED completes the flow — onGranted fires with PARTIAL_GRANTED, state resets
            assertEquals(GrantStatus.PARTIAL_GRANTED, receivedStatus,
                "PARTIAL_GRANTED must invoke onGranted with PARTIAL_GRANTED status")
            assertFalse(state.showSettingsGuide,
                "Settings guide is app responsibility after detecting PARTIAL_GRANTED in callback")
        }
    }

    // ── blocked path ───────────────────────────────────────────────────────

    @Test
    fun `CALENDAR DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CALENDAR, scope = testScope)
        handler.request(settingsMessage = "Enable calendar in Settings") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide)
            assertFalse(state.showRationale)
        }
    }

    // ── CALENDAR vs READ_CALENDAR independence ─────────────────────────────

    @Test
    fun `CALENDAR and READ_CALENDAR are independent permissions`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)

        val calendarHandler = calendarHandler()
        val readCalendarHandler = readCalendarHandler()

        var calendarGranted = false
        calendarHandler.request { calendarGranted = true }
        readCalendarHandler.request(settingsMessage = "Enable Calendar in Settings") {}
        advanceUntilIdle()

        assertTrue(calendarGranted, "CALENDAR should be GRANTED")
        assertEquals(AppGrant.CALENDAR, manager.requestedGrants.first())
    }

    @Test
    fun `least privilege - READ_CALENDAR preferred for read-only calendar access`() = testScope.runTest {
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        val handler = readCalendarHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertEquals(AppGrant.READ_CALENDAR, manager.requestedGrants.first())
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun calendarHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.CALENDAR,
        scope = testScope
    )

    private fun readCalendarHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.READ_CALENDAR,
        scope = testScope
    )
}
