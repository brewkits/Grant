package dev.brewkits.grant.calendar.stress

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.calendar.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress tests for concurrent Calendar permission operations.
 *
 * Focuses on PARTIAL_GRANTED flapping — unique to Calendar because iOS 17+
 * introduces a third state (write-only) that can race with request/dismiss cycles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarStressTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `300 concurrent CALENDAR requests do not crash or deadlock`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        var invocations = 0
        repeat(300) { handler.request { invocations++ } }
        advanceUntilIdle()

        assertTrue(invocations >= 1)
    }

    @Test
    fun `300 concurrent READ_CALENDAR requestSuspend all return valid status`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.READ_CALENDAR, this)

        val results = (1..300).map { async { handler.requestSuspend() } }.awaitAll()

        assertEquals(300, results.size)
        results.forEach { status ->
            assertTrue(
                status == GrantStatus.GRANTED || status == GrantStatus.NOT_DETERMINED,
                "Unexpected status: $status"
            )
        }
    }

    @Test
    fun `alternating CALENDAR request and dismiss is stable`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        manager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        repeat(100) {
            handler.request {}
            advanceUntilIdle()
            handler.onDismiss()
            advanceUntilIdle()
        }

        val state = handler.state.value
        assertEquals(false, state.isVisible)
        assertEquals(false, state.showRationale)
        assertEquals(false, state.showSettingsGuide)
    }

    @Test
    fun `PARTIAL_GRANTED flapping does not corrupt CALENDAR handler state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        // Simulate iOS 17 user toggling between write-only and full access
        repeat(100) {
            manager.mockStatus = when (it % 3) {
                0 -> GrantStatus.NOT_DETERMINED
                1 -> GrantStatus.PARTIAL_GRANTED
                else -> GrantStatus.GRANTED
            }
            handler.refreshStatus()
        }
        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `CALENDAR and READ_CALENDAR concurrent requests do not interfere`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val calendarHandler = GrantHandler(manager, AppGrant.CALENDAR, this)
        val readCalendarHandler = GrantHandler(manager, AppGrant.READ_CALENDAR, this)

        var calendarInvocations = 0
        var readCalendarInvocations = 0

        repeat(50) {
            calendarHandler.request { calendarInvocations++ }
            readCalendarHandler.request { readCalendarInvocations++ }
        }
        advanceUntilIdle()

        assertTrue(calendarInvocations >= 1)
        assertTrue(readCalendarInvocations >= 1)
    }

    @Test
    fun `500 refreshStatus calls with PARTIAL_GRANTED preserve consistent state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        repeat(500) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }
}
