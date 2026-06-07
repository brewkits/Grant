package dev.brewkits.grant.location.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.location.fakes.FakeGrantManager
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
 * End-to-end system tests for always-location in realistic app scenarios:
 * - Background geofencing requiring LOCATION_ALWAYS, granted on first request
 * - Blocked flow (DENIED_ALWAYS) directing the user to Settings
 * - Foreground → background escalation requested as a group
 * - Recovery after an initial DENIED
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationAlwaysSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `geofencing flow - LOCATION_ALWAYS granted on first request starts tracking`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var trackingStarted = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.LOCATION_ALWAYS, scope = testScope)

        handler.request { trackingStarted = true }
        advanceUntilIdle()

        assertTrue(trackingStarted, "Background tracking should start after always-location granted")
        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.LOCATION_ALWAYS, manager.requestedGrants.single())
    }

    @Test
    fun `geofencing blocked - DENIED_ALWAYS requires Settings`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)

        val handler = GrantHandler(grantManager = manager, grant = AppGrant.LOCATION_ALWAYS, scope = testScope)

        handler.request(settingsMessage = "Please enable Always location in Settings to use geofencing") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide,
            "Settings guide must appear when always-location is permanently denied")
    }

    @Test
    fun `foreground then background escalation - both requested as a group`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS),
            scope = testScope
        )

        groupHandler.request {}
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.LOCATION])
        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.LOCATION_ALWAYS])
    }

    @Test
    fun `recovery - DENIED then user grants on second attempt`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.LOCATION_ALWAYS, scope = testScope)

        handler.request {}
        advanceUntilIdle()

        // Second attempt — user grants permission
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var trackingStarted = false
        handler.request { trackingStarted = true }
        advanceUntilIdle()

        assertTrue(trackingStarted, "Tracking should succeed on retry after grant")
    }

    @Test
    fun `requestSuspend returns status directly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.LOCATION_ALWAYS, scope = testScope)

        val result = handler.requestSuspend()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, result)
    }
}
