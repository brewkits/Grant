package dev.brewkits.grant.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantAndServiceChecker
import dev.brewkits.grant.GrantAndServiceHandler
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.LocationReadyStatus
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.testing.FakeGrantManager
import dev.brewkits.grant.testing.FakeServiceManager
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
 * System tests for `grant-core`'s complete public surface, exercised the way a real app
 * combines it — not one class in isolation (that is `integration/`), but a realistic feature
 * built from several of [GrantHandler], [GrantGroupHandler], [GrantAndServiceHandler],
 * [GrantAndServiceChecker], and [RawPermission] together.
 *
 * This directory did not exist before 2026-09; `grant-core` — the module every other module's
 * `system/` folder is modeled on (see grant-contacts, grant-calendar, grant-motion,
 * grant-bluetooth, grant-location-always) — was itself missing one, against the taxonomy
 * CLAUDE.md documents ("system/ — Full end-to-end system tests").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantCoreSystemTest {

    private val testScope = TestScope(StandardTestDispatcher())
    private lateinit var manager: FakeGrantManager
    private lateinit var serviceManager: FakeServiceManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        serviceManager = FakeServiceManager()
    }

    @Test
    fun `video call feature - camera and microphone requested as one group`() = testScope.runTest {
        // The exact scenario the README's "Request several permissions as one unit" section
        // documents: a video call needs both, and must not half-succeed.
        manager.configure(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var callJoined = false
        val callGrants = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope,
        )

        callGrants.request(
            rationaleMessages = mapOf(
                AppGrant.CAMERA to "Camera needed so others can see you.",
                AppGrant.MICROPHONE to "Microphone needed so others can hear you.",
            ),
        ) { callJoined = true }
        advanceUntilIdle()

        assertTrue(callJoined, "call must join only when both grants succeed")
        assertEquals(GrantStatus.GRANTED, callGrants.statuses.value[AppGrant.CAMERA])
        assertEquals(GrantStatus.GRANTED, callGrants.statuses.value[AppGrant.MICROPHONE])
    }

    @Test
    fun `video call feature - camera denied means the call never joins even if mic succeeds`() = testScope.runTest {
        manager.configure(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED, GrantStatus.DENIED)
        manager.configure(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var callJoined = false
        val callGrants = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope,
        )

        callGrants.request { callJoined = true }
        advanceUntilIdle()

        assertTrue(!callJoined, "onAllGranted must not fire when any grant in the group failed")
    }

    @Test
    fun `AR feature - location tracking blocked when GPS hardware is off despite permission granted`() = testScope.runTest {
        // The exact case GrantAndServiceChecker exists for: a granted permission is useless
        // with the hardware service disabled.
        manager.configure(AppGrant.LOCATION, GrantStatus.GRANTED, GrantStatus.GRANTED)
        serviceManager.mockStatus = ServiceStatus.DISABLED

        val checker = GrantAndServiceChecker(grantManager = manager, serviceManager = serviceManager)
        val ready = checker.checkLocationReady(AppGrant.LOCATION)

        assertTrue(ready is LocationReadyStatus.ServiceDisabled, "expected ServiceDisabled, got $ready")
    }

    @Test
    fun `AR feature - location tracking ready when both permission and GPS are on`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION, GrantStatus.GRANTED, GrantStatus.GRANTED)
        serviceManager.mockStatus = ServiceStatus.ENABLED

        val checker = GrantAndServiceChecker(grantManager = manager, serviceManager = serviceManager)
        val ready = checker.checkLocationReady(AppGrant.LOCATION)

        assertTrue(ready is LocationReadyStatus.Ready, "expected Ready, got $ready")
    }

    @Test
    fun `AR feature - both permission and service missing reported distinctly`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        serviceManager.mockStatus = ServiceStatus.DISABLED

        val checker = GrantAndServiceChecker(grantManager = manager, serviceManager = serviceManager)
        val ready = checker.checkLocationReady(AppGrant.LOCATION)

        assertTrue(ready is LocationReadyStatus.BothRequired, "expected BothRequired, got $ready")
    }

    @Test
    fun `reactive location feature - GrantAndServiceHandler drives UI state through StateFlow`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        serviceManager.mockStatus = ServiceStatus.ENABLED

        val handler = GrantAndServiceHandler(
            grantManager = manager,
            serviceManager = serviceManager,
            grant = AppGrant.LOCATION,
            serviceType = ServiceType.LOCATION_GPS,
            scope = testScope,
        )

        var readyFired = false
        handler.request { readyFired = true }
        advanceUntilIdle()

        assertTrue(readyFired, "onReady callback must fire once permission and service line up")
        assertTrue(handler.state.value.isReady, "state should report ready once permission and service line up")
    }

    @Test
    fun `custom OS permission via RawPermission integrates with the same GrantHandler API`() = testScope.runTest {
        // The documented escape hatch for a permission the library doesn't ship as an AppGrant.
        val custom = RawPermission(
            identifier = "custom.usb.permission",
            androidPermissions = listOf("com.example.permission.USB_ACCESS"),
            iosUsageKey = null,
        )
        manager.configure(custom, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var usbConnected = false
        val handler = GrantHandler(grantManager = manager, grant = custom, scope = testScope)
        handler.request { usbConnected = true }
        advanceUntilIdle()

        assertTrue(usbConnected, "RawPermission must flow through GrantHandler exactly like AppGrant")
    }

    @Test
    fun `settings-recovery feature - user grants after DENIED_ALWAYS is reflected without recreating the handler`() = testScope.runTest {
        // Realistic app pattern: show a settings-guide screen, user leaves the app, changes
        // the permission, comes back — the SAME handler instance must pick up the new state
        // on refreshStatus(), not require a fresh GrantHandler.
        manager.configure(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CAMERA, scope = testScope)

        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)

        // User returns from Settings having enabled it. setStatus() overrides the per-grant
        // entry configure() made in the arrange step above — mockStatus is only the fallback
        // used when no per-grant entry exists, so reassigning it would not have changed
        // what this specific grant resolves to.
        manager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        handler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
