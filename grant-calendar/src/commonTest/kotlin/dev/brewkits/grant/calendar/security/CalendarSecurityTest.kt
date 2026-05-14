package dev.brewkits.grant.calendar.security

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security tests for the Calendar permission module.
 *
 * - SEC-CA01: Callback cleared on dismiss (no memory leak)
 * - SEC-CA02: CALENDAR and READ_CALENDAR status are strictly isolated
 * - SEC-CA03: PARTIAL_GRANTED does not escalate to GRANTED automatically
 * - SEC-CA04: Multiple initialize() calls are idempotent
 * - SEC-CA05: DENIED_ALWAYS never triggers system dialog
 * - SEC-CA06: PARTIAL_GRANTED callback fires with correct status (not GRANTED)
 * - SEC-CA07: Repeated DENIED_ALWAYS requests never bypass block
 * - SEC-CA08: Status is stable across repeated refreshStatus calls
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `SEC-CA01 - callback cleared on dismiss and not invoked after late grant`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var invoked = false
        handler.request(settingsMessage = "Enable Calendar") { invoked = true }
        advanceUntilIdle()

        handler.onDismiss()
        advanceUntilIdle()

        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertFalse(invoked, "Callback dismissed before grant — must not fire retroactively")
    }

    @Test
    fun `SEC-CA02 - CALENDAR and READ_CALENDAR status are fully isolated`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED_ALWAYS)
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.GRANTED)

        val calendarHandler = GrantHandler(manager, AppGrant.CALENDAR, this)
        val readCalendarHandler = GrantHandler(manager, AppGrant.READ_CALENDAR, this)

        calendarHandler.refreshStatus()
        readCalendarHandler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, calendarHandler.status.value,
            "CALENDAR status must not leak into READ_CALENDAR")
        assertEquals(GrantStatus.GRANTED, readCalendarHandler.status.value,
            "READ_CALENDAR status must not be affected by CALENDAR state")
    }

    @Test
    fun `SEC-CA03 - PARTIAL_GRANTED does not auto-escalate to GRANTED`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.PARTIAL_GRANTED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        handler.request {}
        advanceUntilIdle()

        val status = handler.status.value
        assertFalse(status == GrantStatus.GRANTED,
            "PARTIAL_GRANTED must never silently upgrade to GRANTED")
        assertEquals(GrantStatus.PARTIAL_GRANTED, status)
    }

    @Test
    fun `SEC-CA04 - multiple initialize calls do not double-register or corrupt state`() = testScope.runTest {
        repeat(10) { GrantCalendar.initialize() }

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "Handler must work normally after repeated initialize() calls")
    }

    @Test
    fun `SEC-CA05 - DENIED_ALWAYS never triggers system dialog request`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        repeat(5) {
            handler.request(settingsMessage = "Enable Calendar in Settings") {}
            advanceUntilIdle()
        }

        val requestCount = manager.requestedGrants.count { it == AppGrant.CALENDAR }
        assertEquals(0, requestCount, "DENIED_ALWAYS must not trigger any system dialog")
    }

    @Test
    fun `SEC-CA06 - PARTIAL_GRANTED callback receives PARTIAL_GRANTED not GRANTED`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var receivedStatus: GrantStatus? = null
        handler.request { status -> receivedStatus = status }
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, receivedStatus,
            "Callback must receive PARTIAL_GRANTED, not GRANTED — caller must distinguish write-only from full access")
    }

    @Test
    fun `SEC-CA07 - repeated DENIED_ALWAYS requests never bypass block`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var grantedCalled = false
        repeat(20) {
            handler.request(settingsMessage = "Enable") { grantedCalled = true }
            advanceUntilIdle()
        }

        assertFalse(grantedCalled, "DENIED_ALWAYS must never invoke the granted callback")
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }

    @Test
    fun `SEC-CA08 - status stable across repeated refreshStatus calls`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        repeat(20) {
            handler.refreshStatus()
            advanceUntilIdle()
            assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value,
                "PARTIAL_GRANTED status must be stable across repeated refresh calls")
        }
    }

    @Test
    fun `SEC-CA09 - handler state is never null`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)

        handler.refreshStatus()
        advanceUntilIdle()

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)
    }

    @Test
    fun `SEC-CA10 - settings confirmation does not grant without explicit refresh`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.PARTIAL_GRANTED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        handler.request {}
        advanceUntilIdle()

        val statusBeforeConfirm = handler.status.value

        manager.configure(AppGrant.CALENDAR, GrantStatus.GRANTED, GrantStatus.GRANTED)
        handler.onSettingsConfirmed()
        advanceUntilIdle()

        assertEquals(statusBeforeConfirm, handler.status.value,
            "onSettingsConfirmed alone must not update status — refreshStatus required")

        handler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value,
            "Status must update to GRANTED only after explicit refreshStatus call")
    }
}
