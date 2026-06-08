package dev.brewkits.grant.bluetooth.security

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security / safety invariants for the Bluetooth module.
 *
 * - SEC-BT01: Callback cleared on dismiss (no retroactive invocation / leak)
 * - SEC-BT02: Central and advertise status are strictly isolated
 * - SEC-BT03: Concurrent handlers cannot corrupt each other's callbacks
 * - SEC-BT04: Multiple initialize() calls are idempotent (no double-registration)
 * - SEC-BT05: DENIED_ALWAYS never triggers a system dialog (no privilege bypass)
 * - SEC-BT06: Repeated requests after DENIED_ALWAYS never grant
 * - SEC-BT07: onGranted fires at most once per request cycle
 * - SEC-BT08: Status stays consistent across repeated refresh
 * - SEC-BT09: Handler state/status are never null
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `SEC-BT01 - callback cleared on dismiss and not invoked after late grant`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        var invoked = false
        handler.request(settingsMessage = "Enable Bluetooth") { invoked = true }
        advanceUntilIdle()

        handler.onDismiss()
        advanceUntilIdle()

        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertFalse(invoked, "Callback dismissed before grant — must not fire retroactively")
    }

    @Test
    fun `SEC-BT02 - central and advertise status are fully isolated`() = testScope.runTest {
        manager.setStatus(AppGrant.BLUETOOTH, GrantStatus.DENIED_ALWAYS)
        manager.setStatus(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.GRANTED)

        val central = GrantHandler(manager, AppGrant.BLUETOOTH, this)
        val advertise = GrantHandler(manager, AppGrant.BLUETOOTH_ADVERTISE, this)

        central.refreshStatus()
        advertise.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, central.status.value,
            "Central status must not be affected by advertise state")
        assertEquals(GrantStatus.GRANTED, advertise.status.value,
            "Advertise status must not be affected by central state")
    }

    @Test
    fun `SEC-BT03 - concurrent handlers do not corrupt each other's callbacks`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        var c1 = 0
        var c2 = 0
        val h1 = GrantHandler(manager, AppGrant.BLUETOOTH, this)
        val h2 = GrantHandler(manager, AppGrant.BLUETOOTH_ADVERTISE, this)

        repeat(10) {
            h1.request { c1++ }
            h2.request { c2++ }
        }
        advanceUntilIdle()

        assertTrue(c1 >= 1, "Central handler must fire at least once")
        assertTrue(c2 >= 1, "Advertise handler must fire at least once")
    }

    @Test
    fun `SEC-BT04 - multiple initialize calls do not double-register or corrupt state`() = testScope.runTest {
        repeat(10) { GrantBluetooth.initialize() }

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "Handler must work normally after repeated initialize() calls")
    }

    @Test
    fun `SEC-BT05 - DENIED_ALWAYS does not trigger a system dialog`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        repeat(5) {
            handler.request(settingsMessage = "Enable Bluetooth in Settings") {}
            advanceUntilIdle()
        }

        val requestCount = manager.requestedGrants.count { it == AppGrant.BLUETOOTH }
        assertEquals(0, requestCount, "DENIED_ALWAYS must never trigger a system dialog request")
    }

    @Test
    fun `SEC-BT06 - repeated requests after DENIED_ALWAYS do not bypass block`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        var grantedCalled = false
        repeat(20) {
            handler.request(settingsMessage = "Enable") { grantedCalled = true }
            advanceUntilIdle()
        }

        assertFalse(grantedCalled, "DENIED_ALWAYS must never invoke the granted callback")
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }

    @Test
    fun `SEC-BT07 - onGranted callback fires at most once per request cycle`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        var callCount = 0
        handler.request { callCount++ }
        advanceUntilIdle()

        assertEquals(1, callCount, "onGranted must fire exactly once per request when already GRANTED")
    }

    @Test
    fun `SEC-BT08 - status remains consistent across refreshStatus calls`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        repeat(20) {
            handler.refreshStatus()
            advanceUntilIdle()
            assertEquals(GrantStatus.GRANTED, handler.status.value,
                "Status must remain stable across repeated refresh calls")
        }
    }

    @Test
    fun `SEC-BT09 - handler state and status are never null`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.BLUETOOTH, this)

        assertNotNull(handler.state.value, "State must never be null")
        assertNotNull(handler.status.value, "Status must never be null")

        handler.refreshStatus()
        advanceUntilIdle()

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)
    }
}
