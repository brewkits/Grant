package dev.brewkits.grant.calendar.performance

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
 * Performance tests for Calendar permission operations.
 *
 * Validates that PARTIAL_GRANTED paths (unique to Calendar on iOS 17+) do not
 * introduce O(n²) behavior in the state machine under high iteration counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarPerformanceTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `1000 sequential checkStatus CALENDAR calls complete`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        repeat(1000) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `1000 parallel requestSuspend CALENDAR calls all resolve`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        val results = (1..1000).map { async { handler.requestSuspend() } }.awaitAll()

        assertEquals(1000, results.size)
        results.forEach { status ->
            assertTrue(
                status == GrantStatus.GRANTED || status == GrantStatus.NOT_DETERMINED,
                "Unexpected: $status"
            )
        }
    }

    @Test
    fun `1000 sequential PARTIAL_GRANTED refreshes do not degrade`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, this)

        repeat(1000) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    @Test
    fun `100 independent CALENDAR handlers do not corrupt each other`() = testScope.runTest {
        val handlers = (1..100).map {
            GrantHandler(FakeGrantManager(mockStatus = GrantStatus.GRANTED), AppGrant.CALENDAR, this)
        }

        handlers.forEach { it.refreshStatus() }
        advanceUntilIdle()

        handlers.forEach { handler ->
            assertEquals(GrantStatus.GRANTED, handler.status.value)
        }
    }
}
