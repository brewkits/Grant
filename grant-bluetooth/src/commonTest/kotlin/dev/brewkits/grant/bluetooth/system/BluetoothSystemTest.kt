package dev.brewkits.grant.bluetooth.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
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
import kotlin.test.assertTrue

/**
 * End-to-end system tests for Bluetooth in realistic app scenarios:
 * - BLE scanning requiring BLUETOOTH, granted on first request
 * - Peripheral advertising requiring only BLUETOOTH_ADVERTISE (no over-request)
 * - Blocked flow (DENIED_ALWAYS) directing the user to Settings
 * - Scan + advertise requested together as a group
 * - Recovery after an initial DENIED
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `scan flow - BLUETOOTH granted on first request starts scanning`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var scanStarted = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH, scope = testScope)

        handler.request { scanStarted = true }
        advanceUntilIdle()

        assertTrue(scanStarted, "Scanning should start after Bluetooth granted")
        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.BLUETOOTH, manager.requestedGrants.single())
    }

    @Test
    fun `advertise flow - only BLUETOOTH_ADVERTISE requested and central never over-requested`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var advertisingStarted = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH_ADVERTISE, scope = testScope)

        handler.request { advertisingStarted = true }
        advanceUntilIdle()

        assertTrue(advertisingStarted)
        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.BLUETOOTH_ADVERTISE, manager.requestedGrants.single())
    }

    @Test
    fun `scan blocked - BLUETOOTH DENIED_ALWAYS requires Settings`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)

        val handler = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH, scope = testScope)

        handler.request(settingsMessage = "Please enable Bluetooth in Settings to scan for devices") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide,
            "Settings guide must appear when Bluetooth is permanently denied")
    }

    @Test
    fun `scan + advertise batch - both permissions requested as a group`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.BLUETOOTH_ADVERTISE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.BLUETOOTH, AppGrant.BLUETOOTH_ADVERTISE),
            scope = testScope
        )

        groupHandler.request {}
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.BLUETOOTH])
        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.BLUETOOTH_ADVERTISE])
    }

    @Test
    fun `recovery - DENIED then user grants on second attempt`() = testScope.runTest {
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH, scope = testScope)

        handler.request {}
        advanceUntilIdle()

        // Second attempt — user grants permission
        manager.configure(AppGrant.BLUETOOTH, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var scanStarted = false
        handler.request { scanStarted = true }
        advanceUntilIdle()

        assertTrue(scanStarted, "Scanning should succeed on retry after grant")
    }

    @Test
    fun `requestSuspend returns status directly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.BLUETOOTH, scope = testScope)

        val result = handler.requestSuspend()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, result)
    }
}
