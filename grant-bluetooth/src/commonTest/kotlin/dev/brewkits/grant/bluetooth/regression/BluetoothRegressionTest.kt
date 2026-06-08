package dev.brewkits.grant.bluetooth.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.bluetooth.GrantBluetooth
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the v2.2.0 module split (Issue #45) and Bluetooth edge cases.
 *
 * Behavior that must NOT regress:
 * - GrantBluetooth.initialize() is idempotent
 * - BLUETOOTH and BLUETOOTH_ADVERTISE stay distinct after the module move
 * - Each handler requests only its own permission (no cross-request)
 * - DENIED state does not permanently block subsequent requests
 * - Central and advertise state machines are completely independent
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── v2.2.0 module split regressions ────────────────────────────────────

    @Test
    fun `v2_2 - GrantBluetooth initialize is idempotent`() {
        GrantBluetooth.initialize()
        GrantBluetooth.initialize()
        GrantBluetooth.initialize()
        // No assertion needed — absence of exception is the contract.
    }

    @Test
    fun `v2_2 - BLUETOOTH and BLUETOOTH_ADVERTISE have distinct identifiers`() {
        assertNotEquals(
            AppGrant.BLUETOOTH.identifier,
            AppGrant.BLUETOOTH_ADVERTISE.identifier,
            "Central and peripheral Bluetooth must stay distinct after being moved to their own module"
        )
    }

    @Test
    fun `v2_2 - central requests only BLUETOOTH and advertise only BLUETOOTH_ADVERTISE`() = testScope.runTest {
        manager.mockRequestResult = GrantStatus.GRANTED
        manager.mockStatus = GrantStatus.NOT_DETERMINED

        val central = GrantHandler(manager, AppGrant.BLUETOOTH, testScope)
        central.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.BLUETOOTH, manager.requestedGrants[0],
            "Central handler must only request AppGrant.BLUETOOTH")

        manager.reset()

        val advertise = GrantHandler(manager, AppGrant.BLUETOOTH_ADVERTISE, testScope)
        advertise.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.BLUETOOTH_ADVERTISE, manager.requestedGrants[0],
            "Advertise handler must only request AppGrant.BLUETOOTH_ADVERTISE")
    }

    // ── state machine regressions ──────────────────────────────────────────

    @Test
    fun `DENIED state does not permanently block subsequent request`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, testScope)

        handler.request {}
        advanceUntilIdle()

        // Simulate user granting permission manually (e.g., in Settings)
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "DENIED state must not permanently block future requests")
    }

    @Test
    fun `onDismiss after DENIED clears dialog state without requesting again`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, testScope)

        handler.request {}
        advanceUntilIdle()
        val requestCountAfterFirst = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        val state = handler.state.value
        assertFalse(state.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(requestCountAfterFirst, manager.requestedGrants.size,
            "Dismiss must not trigger another request")
    }

    @Test
    fun `central and advertise state machines are completely independent`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        manager.configure(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val central = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH, scope = testScope)
        val advertise = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH_ADVERTISE, scope = testScope)

        central.request(settingsMessage = "Enable in Settings") {}
        var advertiseGranted = false
        advertise.request { advertiseGranted = true }
        advanceUntilIdle()

        assertTrue(central.state.value.showSettingsGuide,
            "Central handler should show settings guide (DENIED_ALWAYS)")
        assertTrue(advertiseGranted,
            "Advertise handler should succeed independently of central state")
    }

    @Test
    fun `refreshStatus after GRANTED stays GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, testScope)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)

        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
