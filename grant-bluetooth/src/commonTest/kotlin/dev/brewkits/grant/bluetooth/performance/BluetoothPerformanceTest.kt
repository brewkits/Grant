package dev.brewkits.grant.bluetooth.performance

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.bluetooth.fakes.FakeGrantManager
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
 * Performance tests for Bluetooth permission operations.
 *
 * These use TestScope virtual time to verify operations complete within
 * iteration bounds without stack overflow or unbounded growth. They do NOT
 * measure wall-clock time — that is out of scope for common tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothPerformanceTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `1000 sequential refreshStatus calls all complete`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        repeat(1000) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `1000 parallel requestSuspend calls for GRANTED permission all resolve`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        val results = (1..1000).map { async { handler.requestSuspend() } }.awaitAll()

        assertEquals(1000, results.size)
        results.forEach { status ->
            assertTrue(
                status == GrantStatus.GRANTED || status == GrantStatus.NOT_DETERMINED,
                "Unexpected status: $status"
            )
        }
    }

    @Test
    fun `creating 100 independent handlers does not corrupt shared state`() = testScope.runTest {
        val handlers = (1..100).map {
            GrantHandler(FakeGrantManager(mockStatus = GrantStatus.GRANTED), AppGrant.BLUETOOTH, this)
        }

        handlers.forEach { it.refreshStatus() }
        advanceUntilIdle()

        handlers.forEach { handler ->
            assertEquals(GrantStatus.GRANTED, handler.status.value,
                "Each handler should independently track GRANTED status")
        }
    }

    @Test
    fun `500 sequential central + advertise requests complete and fire callbacks`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        var totalCallbacks = 0
        val central = GrantHandler(manager, AppGrant.BLUETOOTH, this)
        val advertise = GrantHandler(manager, AppGrant.BLUETOOTH_ADVERTISE, this)

        repeat(500) {
            central.request { totalCallbacks++ }
            advertise.request { totalCallbacks++ }
        }
        advanceUntilIdle()

        assertTrue(totalCallbacks >= 2, "At least one callback per handler must fire")
    }
}
