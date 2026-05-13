package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import dev.brewkits.grant.fakes.FakeServiceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GrantAndServiceHandlerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeGrantManager: FakeGrantManager
    private lateinit var fakeServiceManager: FakeServiceManager
    private lateinit var handler: GrantAndServiceHandler

    @BeforeTest
    fun setup() {
        fakeGrantManager = FakeGrantManager()
        fakeServiceManager = FakeServiceManager()
        handler = GrantAndServiceHandler(
            grantManager = fakeGrantManager,
            serviceManager = fakeServiceManager,
            grant = AppGrant.LOCATION,
            serviceType = ServiceType.LOCATION_GPS,
            scope = testScope
        )
    }

    @AfterTest
    fun teardown() {
        fakeGrantManager.reset()
    }

    // ─── Success flows ──────────────────────────────────────────────────────

    @Test
    fun `success - permission granted and service enabled fires onReady`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        var readyCalled = false
        handler.request { readyCalled = true }
        testScope.advanceUntilIdle()

        assertTrue(readyCalled, "onReady should fire when both permission and service are ready")
        assertFalse(handler.state.value.isVisible, "No dialog should be shown")
        assertTrue(handler.state.value.isReady, "isReady should be true")
    }

    @Test
    fun `success - NOT_DETERMINED grants first then service enabled fires onReady`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        fakeGrantManager.mockRequestResult = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        var readyCalled = false
        handler.request { readyCalled = true }
        testScope.advanceUntilIdle()

        assertTrue(readyCalled, "onReady should fire after system grant dialog")
    }

    @Test
    fun `success - PARTIAL_GRANTED also fires onReady`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.PARTIAL_GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        var readyCalled = false
        handler.request { readyCalled = true }
        testScope.advanceUntilIdle()

        assertTrue(readyCalled, "PARTIAL_GRANTED should be accepted as sufficient")
    }

    // ─── Permission denied flows ─────────────────────────────────────────────

    @Test
    fun `permission DENIED shows rationale dialog`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.DENIED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        handler.request(rationaleMessage = "We need location") { }
        testScope.advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.isVisible, "Dialog should be visible")
        assertTrue(state.showRationale, "Rationale should be shown")
        assertEquals("We need location", state.rationaleMessage, "Custom rationale message must be preserved")
    }

    @Test
    fun `permission DENIED_ALWAYS shows settings guide`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        handler.request(permissionSettingsMessage = "Go to Settings") { }
        testScope.advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.isVisible, "Dialog should be visible")
        assertTrue(state.showPermissionSettings, "Settings guide should be shown")
        assertEquals("Go to Settings", state.permissionSettingsMessage, "Custom settings message must be preserved")
    }

    @Test
    fun `first system request denied - shows rationale`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        fakeGrantManager.mockRequestResult = GrantStatus.DENIED

        handler.request { }
        testScope.advanceUntilIdle()

        assertTrue(handler.state.value.showRationale, "Rationale should be shown immediately on first denial (matches GrantHandler UX)")
    }

    // ─── Service disabled flow ───────────────────────────────────────────────

    @Test
    fun `service DISABLED shows service settings dialog`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.DISABLED

        handler.request(serviceSettingsMessage = "Turn on GPS") { }
        testScope.advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.isVisible, "Dialog should be visible")
        assertTrue(state.showServiceSettings, "Service settings dialog should be shown")
        assertEquals("Turn on GPS", state.serviceSettingsMessage, "Custom service message must be preserved")
    }

    // ─── onRationaleConfirmed passes messages correctly ─────────────────────

    @Test
    fun `onRationaleConfirmed - if granted then service disabled uses original svc message`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.DENIED

        handler.request(serviceSettingsMessage = "Enable GPS please") { }
        testScope.advanceUntilIdle()
        assertTrue(handler.state.value.showRationale)

        // User confirms rationale, system grants permission but service is off
        fakeGrantManager.mockRequestResult = GrantStatus.GRANTED
        fakeGrantManager.mockStatus = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.DISABLED

        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showServiceSettings, "Should show service settings after permission granted")
        assertEquals("Enable GPS please", state.serviceSettingsMessage, "Original service message must survive onRationaleConfirmed()")
    }

    @Test
    fun `onRationaleConfirmed - if denied_always uses original settings message`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.DENIED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        handler.request(permissionSettingsMessage = "Open App Settings") { }
        testScope.advanceUntilIdle()
        assertTrue(handler.state.value.showRationale)

        fakeGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showPermissionSettings, "Should show permission settings")
        assertEquals("Open App Settings", state.permissionSettingsMessage, "Original permission message must survive")
    }

    // ─── Dismiss flow ────────────────────────────────────────────────────────

    @Test
    fun `onDismiss clears state and does not fire onReady`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.DENIED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        var readyCalled = false
        handler.request { readyCalled = true }
        testScope.advanceUntilIdle()
        assertTrue(handler.state.value.showRationale)

        handler.onDismiss()
        testScope.advanceUntilIdle()

        assertFalse(readyCalled, "onReady must not be called after dismiss")
        assertFalse(handler.state.value.isVisible, "Dialog must be hidden after dismiss")
    }

    // ─── refreshStatus ───────────────────────────────────────────────────────

    @Test
    fun `refreshStatus updates isReady correctly`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.DENIED
        fakeServiceManager.mockStatus = ServiceStatus.DISABLED

        handler.refreshStatus()
        testScope.advanceUntilIdle()
        assertFalse(handler.state.value.isReady, "Not ready when permission denied and service off")

        fakeGrantManager.mockStatus = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        handler.refreshStatus()
        testScope.advanceUntilIdle()
        assertTrue(handler.state.value.isReady, "Ready when both permission and service OK")
    }

    // ─── Concurrent request guard ────────────────────────────────────────────

    @Test
    fun `concurrent request is ignored while one is in flight`() = runTest {
        fakeGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        fakeGrantManager.simulatedDelayMs = 50
        fakeGrantManager.mockRequestResult = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        var callCount = 0
        handler.request { callCount++ }
        handler.request { callCount++ } // should be silently ignored

        testScope.advanceUntilIdle()

        assertEquals(1, callCount, "Only one onReady callback should fire")
    }

    // ─── RawPermission support (Bug 12 regression) ──────────────────────────

    @Test
    fun `accepts RawPermission as grant type`() = runTest {
        val rawPermission = RawPermission(
            identifier = "CUSTOM_PERM",
            androidPermissions = listOf("com.example.CUSTOM")
        )
        val handlerWithRaw = GrantAndServiceHandler(
            grantManager = fakeGrantManager,
            serviceManager = fakeServiceManager,
            grant = rawPermission,
            serviceType = ServiceType.LOCATION_GPS,
            scope = testScope
        )
        fakeGrantManager.mockStatus = GrantStatus.GRANTED
        fakeServiceManager.mockStatus = ServiceStatus.ENABLED

        var readyCalled = false
        handlerWithRaw.request { readyCalled = true }
        testScope.advanceUntilIdle()

        assertTrue(readyCalled, "RawPermission should work with GrantAndServiceHandler")
    }
}
