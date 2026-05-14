package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the v2.0.0 module split of Contacts, Calendar, and Motion.
 *
 * These tests operate at the GrantManager / GrantHandler level (not platform delegate level)
 * and verify the behavioral contracts that must hold regardless of which optional modules
 * are registered:
 *
 * - The three optional permissions (CONTACTS, CALENDAR, MOTION) remain valid AppGrant values
 * - Their identifiers are distinct from each other and from all other permissions
 * - A FakeGrantManager configured with any status correctly flows through GrantHandler
 * - No cross-contamination between optional permissions and core permissions
 *
 * Platform-level registry behavior (IosPermissionHandlerRegistry + NotRegisteredHandler)
 * is covered in IosHandlersExhaustiveTest and ModuleSplitRegistryTest (iosTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class V2ModuleSplitRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── optional permission identifiers are distinct ────────────────────────

    @Test
    fun `optional permissions have unique identifiers`() {
        val optionalPermissions = listOf(
            AppGrant.CONTACTS,
            AppGrant.READ_CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.READ_CALENDAR,
            AppGrant.MOTION
        )
        val identifiers = optionalPermissions.map { it.identifier }
        assertEquals(
            identifiers.size,
            identifiers.toSet().size,
            "All optional permission identifiers must be unique: $identifiers"
        )
    }

    @Test
    fun `optional permissions do not collide with core permissions`() {
        val corePermissions = listOf(
            AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS,
            AppGrant.GALLERY, AppGrant.BLUETOOTH, AppGrant.NOTIFICATION
        )
        val optionalPermissions = listOf(
            AppGrant.CONTACTS, AppGrant.READ_CONTACTS,
            AppGrant.CALENDAR, AppGrant.READ_CALENDAR,
            AppGrant.MOTION
        )

        for (optional in optionalPermissions) {
            for (core in corePermissions) {
                assertNotEquals(
                    optional.identifier, core.identifier,
                    "${optional.name} must not have the same identifier as ${core.name}"
                )
            }
        }
    }

    // ── GrantHandler works correctly with all optional permissions ─────────

    @Test
    fun `CONTACTS handler flows through GrantManager correctly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, testScope)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
        assertEquals(listOf<GrantPermission>(AppGrant.CONTACTS), manager.requestedGrants)
    }

    @Test
    fun `READ_CONTACTS handler flows through GrantManager correctly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.READ_CONTACTS, testScope)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
        assertEquals(listOf<GrantPermission>(AppGrant.READ_CONTACTS), manager.requestedGrants)
    }

    @Test
    fun `CALENDAR handler flows through GrantManager correctly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CALENDAR, testScope)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
        assertEquals(listOf<GrantPermission>(AppGrant.CALENDAR), manager.requestedGrants)
    }

    @Test
    fun `READ_CALENDAR handler flows through GrantManager correctly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.READ_CALENDAR, testScope)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
        assertEquals(listOf<GrantPermission>(AppGrant.READ_CALENDAR), manager.requestedGrants)
    }

    @Test
    fun `MOTION handler flows through GrantManager correctly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, testScope)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
        assertEquals(listOf<GrantPermission>(AppGrant.MOTION), manager.requestedGrants)
    }

    // ── optional permissions do not interfere with each other ──────────────

    @Test
    fun `all five optional permissions can be requested concurrently without interference`() = testScope.runTest {
        val optionalPermissions = listOf(
            AppGrant.CONTACTS, AppGrant.READ_CONTACTS,
            AppGrant.CALENDAR, AppGrant.READ_CALENDAR,
            AppGrant.MOTION
        )
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        var callbackCount = 0
        val handlers = optionalPermissions.map { grant ->
            GrantHandler(manager, grant, testScope).also { h -> h.request { callbackCount++ } }
        }
        advanceUntilIdle()

        assertEquals(5, callbackCount, "All 5 optional permission handlers must fire their callback")
        assertEquals(5, manager.requestedGrants.distinct().size,
            "Each optional permission must be requested exactly once")
    }

    // ── PARTIAL_GRANTED is valid for Calendar, not for others ─────────────

    @Test
    fun `PARTIAL_GRANTED Calendar status flows through GrantHandler`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(manager, AppGrant.CALENDAR, testScope)

        handler.request {}
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    @Test
    fun `MOTION DENIED_ALWAYS shows settings guide matching hardware-unavailable path`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.MOTION,
            scope = testScope
        )

        handler.request(settingsMessage = "Motion unavailable") {}
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showSettingsGuide)
    }
}
