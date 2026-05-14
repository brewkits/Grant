package dev.brewkits.grant.motion.performance

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.motion.fakes.FakeGrantManager
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
 * Performance tests for Motion permission operations.
 *
 * Validates that the dummy-query pattern (unique to iOS CoreMotion) does not
 * introduce unbounded waiting under high iteration counts in the Kotlin state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionPerformanceTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `1000 sequential checkStatus MOTION calls complete`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(1000) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `1000 parallel requestSuspend MOTION calls all resolve`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

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
    fun `100 independent MOTION handlers do not share state`() = testScope.runTest {
        val handlers = (1..100).map {
            GrantHandler(FakeGrantManager(mockStatus = GrantStatus.GRANTED), AppGrant.MOTION, this)
        }

        handlers.forEach { it.refreshStatus() }
        advanceUntilIdle()

        handlers.forEach { handler ->
            assertEquals(GrantStatus.GRANTED, handler.status.value)
        }
    }

    @Test
    fun `1000 sequential DENIED_ALWAYS refreshes complete without growing state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(1000) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }
}
