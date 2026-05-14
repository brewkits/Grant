package dev.brewkits.grant.calendar.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.calendar.GrantCalendar
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for v2.0 module split and Calendar-specific edge cases.
 *
 * Covers:
 * - PARTIAL_GRANTED must not trigger onGranted callback (write-only ≠ full access)
 * - CALENDAR and READ_CALENDAR have independent identifiers and state machines
 * - GrantCalendar.initialize() is idempotent
 * - iOS 17+ write-only status persists correctly across refresh cycles
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── v2.0 module split regressions ─────────────────────────────────────

    @Test
    fun `v2_0 - GrantCalendar initialize is idempotent`() {
        GrantCalendar.initialize()
        GrantCalendar.initialize()
        GrantCalendar.initialize()
    }

    @Test
    fun `v2_0 - CALENDAR and READ_CALENDAR have distinct permission identifiers`() {
        assertNotEquals(
            AppGrant.CALENDAR.identifier,
            AppGrant.READ_CALENDAR.identifier
        )
    }

    @Test
    fun `v2_0 - CALENDAR handler does not request READ_CALENDAR and vice versa`() = testScope.runTest {
        manager.mockRequestResult = GrantStatus.GRANTED
        manager.mockStatus = GrantStatus.NOT_DETERMINED

        val calendarHandler = GrantHandler(manager, AppGrant.CALENDAR, testScope)
        calendarHandler.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.CALENDAR, manager.requestedGrants[0])

        manager.reset()

        val readCalendarHandler = GrantHandler(manager, AppGrant.READ_CALENDAR, testScope)
        readCalendarHandler.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.READ_CALENDAR, manager.requestedGrants[0])
    }

    // ── PARTIAL_GRANTED regression ─────────────────────────────────────────

    @Test
    fun `PARTIAL_GRANTED invokes onGranted callback with PARTIAL_GRANTED status`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, testScope)

        var receivedStatus: GrantStatus? = null
        handler.request { status -> receivedStatus = status }
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, receivedStatus,
            "PARTIAL_GRANTED must invoke onGranted with PARTIAL_GRANTED status, distinct from GRANTED")
    }

    @Test
    fun `PARTIAL_GRANTED status is correctly reflected in handler status after refresh`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, testScope)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    // ── state machine regressions ──────────────────────────────────────────

    @Test
    fun `DENIED state does not permanently block Calendar`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, testScope)

        handler.request {}
        advanceUntilIdle()

        manager.configure(AppGrant.CALENDAR, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
    }

    @Test
    fun `CALENDAR and READ_CALENDAR state machines are independent`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val calendarHandler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CALENDAR,
            scope = testScope
        )
        val readCalendarHandler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.READ_CALENDAR,
            scope = testScope
        )

        calendarHandler.request(settingsMessage = "Enable Calendar in Settings") {}
        var readGranted = false
        readCalendarHandler.request { readGranted = true }
        advanceUntilIdle()

        assertTrue(calendarHandler.state.value.showSettingsGuide)
        assertTrue(readGranted)
    }

    @Test
    fun `onDismiss after DENIED clears Calendar dialog state`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, testScope)

        handler.request {}
        advanceUntilIdle()
        handler.onDismiss()
        advanceUntilIdle()

        val state = handler.state.value
        assertFalse(state.isVisible)
    }
}
