package dev.brewkits.grant.motion.stress

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
 * Stress tests for concurrent Motion permission operations.
 *
 * Motion is unique because the iOS dummy-query pattern uses a real OS callback.
 * These tests verify the Kotlin-level state machine handles concurrent
 * invocations correctly, independent of platform callback timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionStressTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `300 concurrent MOTION requests do not crash or deadlock`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var invocations = 0
        repeat(300) { handler.request { invocations++ } }
        advanceUntilIdle()

        assertTrue(invocations >= 1)
    }

    @Test
    fun `300 concurrent requestSuspend MOTION all return valid status`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

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
    fun `alternating MOTION request and dismiss is stable`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        manager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

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
    fun `rapid status flapping MOTION preserves consistent state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(200) {
            manager.mockStatus = if (it % 2 == 0) GrantStatus.DENIED else GrantStatus.GRANTED
            handler.refreshStatus()
        }
        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `rapid DENIED_ALWAYS requests do not corrupt handler state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(100) {
            handler.refreshStatus()
        }
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }

    @Test
    fun `500 refreshStatus calls MOTION do not corrupt state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        repeat(500) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
