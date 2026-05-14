package dev.brewkits.grant.calendar

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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GrantCalendarTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── Initialization ──────────────────────────────────────────────────────

    @Test
    fun initialize_doesNotThrow() {
        GrantCalendar.initialize()
    }

    @Test
    fun initialize_isIdempotent() {
        GrantCalendar.initialize()
        GrantCalendar.initialize()
        GrantCalendar.initialize()
    }

    // ── AppGrant identifiers ───────────────────────────────────────────────

    @Test
    fun `CALENDAR identifier is non-empty`() {
        assertTrue(AppGrant.CALENDAR.identifier.isNotEmpty())
    }

    @Test
    fun `READ_CALENDAR identifier is non-empty`() {
        assertTrue(AppGrant.READ_CALENDAR.identifier.isNotEmpty())
    }

    @Test
    fun `CALENDAR and READ_CALENDAR have distinct identifiers`() {
        assertNotEquals(AppGrant.CALENDAR.identifier, AppGrant.READ_CALENDAR.identifier)
    }

    @Test
    fun `CALENDAR requiresBackgroundUpgrade is false`() {
        assertFalse(AppGrant.CALENDAR.requiresBackgroundUpgrade)
    }

    @Test
    fun `READ_CALENDAR requiresBackgroundUpgrade is false`() {
        assertFalse(AppGrant.READ_CALENDAR.requiresBackgroundUpgrade)
    }

    // ── GrantHandler basics ────────────────────────────────────────────────

    @Test
    fun `CALENDAR handler initial status is NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `READ_CALENDAR handler initial status is NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.READ_CALENDAR, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `request CALENDAR when GRANTED invokes callback without re-requesting`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var called = false
        handler.request { called = true }
        advanceUntilIdle()

        assertTrue(called)
        assertFalse(manager.requestCalled, "Already GRANTED — no system dialog should be triggered")
    }

    @Test
    fun `PARTIAL_GRANTED status reflects iOS 17 write-only access`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    @Test
    fun `PARTIAL_GRANTED callback receives PARTIAL_GRANTED status`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var receivedStatus: GrantStatus? = null
        handler.request { status -> receivedStatus = status }
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, receivedStatus)
    }

    @Test
    fun `request CALENDAR DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        handler.request(settingsMessage = "Enable Calendar in Settings") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `refreshStatus updates status from manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    @Test
    fun `onDismiss hides dialog without triggering new request`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        handler.request {}
        advanceUntilIdle()
        val countBefore = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        assertFalse(handler.state.value.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(countBefore, manager.requestedGrants.size, "Dismiss must not trigger another request")
    }
}
