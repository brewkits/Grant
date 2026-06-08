package dev.brewkits.grant.bluetooth.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.bluetooth.fakes.FakeGrantManager
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
 * Integration tests for BLUETOOTH and BLUETOOTH_ADVERTISE through GrantHandler.
 *
 * Verifies the full state machine (NOT_DETERMINED → request → result), the blocked
 * path (DENIED_ALWAYS → settings guide), and that central (BLUETOOTH) and peripheral
 * (BLUETOOTH_ADVERTISE) permissions are tracked independently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantBluetoothIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── checkStatus ────────────────────────────────────────────────────────

    @Test
    fun `checkStatus returns valid GrantStatus`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = bluetoothHandler()
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `central and advertise track status independently`() = testScope.runTest {
        manager.setStatus(AppGrant.BLUETOOTH, GrantStatus.GRANTED)
        manager.setStatus(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.NOT_DETERMINED)

        val central = bluetoothHandler()
        val advertise = advertiseHandler()
        central.refreshStatus()
        advertise.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, central.status.value)
        assertEquals(GrantStatus.NOT_DETERMINED, advertise.status.value)
    }

    // ── request happy path ─────────────────────────────────────────────────

    @Test
    fun `request NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = bluetoothHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertTrue(manager.requestCalled)
        assertEquals(AppGrant.BLUETOOTH, manager.requestedGrants.first())
    }

    @Test
    fun `request when already GRANTED does not re-request`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = bluetoothHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertFalse(manager.requestCalled, "Should not call request() when already GRANTED")
    }

    // ── blocked path ───────────────────────────────────────────────────────

    @Test
    fun `DENIED_ALWAYS shows settings guide and not rationale`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = bluetoothHandler()
        handler.request(settingsMessage = "Enable Bluetooth in Settings") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Settings guide should be visible for DENIED_ALWAYS")
            assertFalse(state.showRationale)
        }
    }

    // ── independence between central and peripheral ────────────────────────

    @Test
    fun `central and advertise are independent permissions`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)

        val central = bluetoothHandler()
        val advertise = advertiseHandler()

        var centralGranted = false
        central.request { centralGranted = true }
        advertise.request(settingsMessage = "Enable advertising in Settings") {}
        advanceUntilIdle()

        assertTrue(centralGranted, "BLUETOOTH should be GRANTED independently")
        assertTrue(advertise.state.value.showSettingsGuide,
            "BLUETOOTH_ADVERTISE DENIED_ALWAYS must show settings guide independently")
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun bluetoothHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.BLUETOOTH,
        scope = testScope
    )

    private fun advertiseHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.BLUETOOTH_ADVERTISE,
        scope = testScope
    )
}
