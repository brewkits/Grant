package dev.brewkits.grant.bluetooth

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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the grant-bluetooth module surface: the [GrantBluetooth] initializer
 * and the [AppGrant.BLUETOOTH] / [AppGrant.BLUETOOTH_ADVERTISE] permission contracts,
 * plus basic [GrantHandler] wiring against a fake manager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantBluetoothTest {

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
        GrantBluetooth.initialize()
    }

    @Test
    fun initialize_isIdempotent() {
        GrantBluetooth.initialize()
        GrantBluetooth.initialize()
        GrantBluetooth.initialize()
    }

    // ── AppGrant contracts ──────────────────────────────────────────────────

    @Test
    fun `BLUETOOTH identifier is non-empty`() {
        assertTrue(AppGrant.BLUETOOTH.identifier.isNotEmpty())
    }

    @Test
    fun `BLUETOOTH_ADVERTISE identifier is non-empty`() {
        assertTrue(AppGrant.BLUETOOTH_ADVERTISE.identifier.isNotEmpty())
    }

    @Test
    fun `BLUETOOTH and BLUETOOTH_ADVERTISE have distinct identifiers`() {
        assertNotEquals(
            AppGrant.BLUETOOTH.identifier,
            AppGrant.BLUETOOTH_ADVERTISE.identifier,
            "Central (scan/connect) and peripheral (advertise) Bluetooth must be distinct permissions"
        )
    }

    @Test
    fun `BLUETOOTH does not require background upgrade`() {
        assertFalse(AppGrant.BLUETOOTH.requiresBackgroundUpgrade)
    }

    @Test
    fun `BLUETOOTH_ADVERTISE does not require background upgrade`() {
        assertFalse(AppGrant.BLUETOOTH_ADVERTISE.requiresBackgroundUpgrade)
    }

    // ── GrantHandler basics ────────────────────────────────────────────────

    @Test
    fun `handler initial status reflects manager NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `request when GRANTED invokes callback without re-requesting`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        var called = false
        handler.request { called = true }
        advanceUntilIdle()

        assertTrue(called)
        assertFalse(manager.requestCalled, "Already GRANTED — no system dialog should be triggered")
    }

    @Test
    fun `request NOT_DETERMINED forwards exactly BLUETOOTH to the manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        handler.request {}
        advanceUntilIdle()

        assertTrue(manager.requestCalled)
        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.BLUETOOTH, manager.requestedGrants.single())
    }

    @Test
    fun `request DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        handler.request(settingsMessage = "Enable Bluetooth access in Settings") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `refreshStatus updates status from manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.DENIED, handler.status.value)
    }

    @Test
    fun `onDismiss hides dialog without triggering new request`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        handler.request {}
        advanceUntilIdle()
        val countBefore = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        assertFalse(handler.state.value.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(countBefore, manager.requestedGrants.size, "Dismiss must not trigger another request")
    }
}
