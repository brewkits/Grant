package dev.brewkits.grant.motion.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.motion.fakes.FakeGrantManager
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
 * System tests for Motion permission in realistic usage scenarios.
 *
 * - Step counter / pedometer requiring MOTION
 * - Activity recognition requiring MOTION
 * - MOTION + LOCATION combined for fitness tracking
 * - Hardware unavailability path (no physical motion chip)
 * - Post-denial recovery
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `pedometer flow - MOTION granted and step tracking starts`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        var pedometerStarted = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.MOTION, scope = testScope)

        handler.request { pedometerStarted = true }
        advanceUntilIdle()

        assertTrue(pedometerStarted)
    }

    @Test
    fun `activity recognition - MOTION required and blocked when hardware unavailable`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.MOTION,
            scope = testScope
        )

        handler.request(settingsMessage = "Activity recognition unavailable") {}
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showSettingsGuide, "Hardware unavailable path must block access gracefully")
    }

    @Test
    fun `fitness tracking - MOTION + LOCATION requested together`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.MOTION, AppGrant.LOCATION),
            scope = testScope
        )

        groupHandler.request {}
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.MOTION])
        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.LOCATION])
    }

    @Test
    fun `motion recovery - DENIED then user re-grants via settings`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.MOTION, scope = testScope)

        handler.request {}
        advanceUntilIdle()

        manager.configure(AppGrant.MOTION, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
    }

    @Test
    fun `requestSuspend MOTION works in coroutine context`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.MOTION, scope = testScope)

        val result = handler.requestSuspend()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `motion check status when GRANTED short-circuits without requesting`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.GRANTED, GrantStatus.GRANTED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.MOTION, scope = testScope)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        // Request was already granted - no system dialog needed
        assertEquals(false, manager.requestCalled)
    }
}
