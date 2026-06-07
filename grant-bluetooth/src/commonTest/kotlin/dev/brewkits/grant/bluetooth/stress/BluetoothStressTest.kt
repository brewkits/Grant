package dev.brewkits.grant.bluetooth.stress

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
 * Stress tests for concurrent Bluetooth operations: concurrent requests to one
 * handler, concurrent central + advertise handlers, rapid request/dismiss cycles,
 * and rapid status flapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothStressTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `300 concurrent requests do not crash or deadlock`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        var invocations = 0
        repeat(300) { handler.request { invocations++ } }
        advanceUntilIdle()

        assertTrue(invocations >= 1, "At least one callback must fire")
    }

    @Test
    fun `300 concurrent requestSuspend all return valid status`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

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
    fun `concurrent central and advertise requests do not interfere`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val central = GrantHandler(manager, AppGrant.BLUETOOTH, this)
        val advertise = GrantHandler(manager, AppGrant.BLUETOOTH_ADVERTISE, this)

        var centralInvocations = 0
        var advertiseInvocations = 0

        repeat(50) {
            central.request { centralInvocations++ }
            advertise.request { advertiseInvocations++ }
        }
        advanceUntilIdle()

        assertTrue(centralInvocations >= 1)
        assertTrue(advertiseInvocations >= 1)
    }

    @Test
    fun `alternating request and dismiss is stable`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        manager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

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
    fun `rapid status flapping preserves consistent final state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

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
    fun `500 refreshStatus calls do not corrupt state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        repeat(500) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
