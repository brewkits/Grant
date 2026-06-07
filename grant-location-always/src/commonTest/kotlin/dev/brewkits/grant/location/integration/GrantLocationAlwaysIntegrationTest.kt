package dev.brewkits.grant.location.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for LOCATION_ALWAYS through GrantHandler.
 *
 * Verifies the complete state machine (NOT_DETERMINED → request → result),
 * the blocked path (DENIED_ALWAYS → settings guide), and that the always-location
 * handler is fully independent from the foreground LOCATION handler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantLocationAlwaysIntegrationTest {

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
        val handler = alwaysHandler()
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `always and foreground location track status independently`() = testScope.runTest {
        manager.setStatus(AppGrant.LOCATION_ALWAYS, GrantStatus.NOT_DETERMINED)
        manager.setStatus(AppGrant.LOCATION, GrantStatus.GRANTED)

        val always = alwaysHandler()
        val foreground = foregroundHandler()
        always.refreshStatus()
        foreground.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.NOT_DETERMINED, always.status.value)
        assertEquals(GrantStatus.GRANTED, foreground.status.value)
    }

    // ── request happy path ─────────────────────────────────────────────────

    @Test
    fun `request NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = alwaysHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertTrue(manager.requestCalled)
        assertEquals(AppGrant.LOCATION_ALWAYS, manager.requestedGrants.first())
    }

    @Test
    fun `request when already GRANTED does not re-request`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = alwaysHandler()
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
        val handler = alwaysHandler()
        handler.request(settingsMessage = "Enable Always location in Settings") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Settings guide should be visible for DENIED_ALWAYS")
            assertFalse(state.showRationale)
        }
    }

    // ── independence from foreground LOCATION ──────────────────────────────

    @Test
    fun `granting always-location does not request foreground LOCATION`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val handler = alwaysHandler()
        var granted = false
        handler.request { granted = true }
        advanceUntilIdle()

        assertTrue(granted)
        assertEquals(1, manager.requestedGrants.size,
            "Always-location handler must only request LOCATION_ALWAYS, never foreground LOCATION")
        assertEquals(AppGrant.LOCATION_ALWAYS, manager.requestedGrants.single())
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun alwaysHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.LOCATION_ALWAYS,
        scope = testScope
    )

    private fun foregroundHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.LOCATION,
        scope = testScope
    )
}
