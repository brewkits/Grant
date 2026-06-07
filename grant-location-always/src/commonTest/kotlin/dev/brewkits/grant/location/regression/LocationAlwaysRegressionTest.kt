package dev.brewkits.grant.location.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.location.GrantLocationAlways
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the v2.2.0 module split (Issue #45) and always-location edge cases.
 *
 * Behavior that must NOT regress:
 * - GrantLocationAlways.initialize() is idempotent
 * - LOCATION_ALWAYS stays distinct from foreground LOCATION
 * - LOCATION_ALWAYS keeps requiresBackgroundUpgrade == true after the module move
 * - The handler only ever requests LOCATION_ALWAYS (never escalates to foreground)
 * - DENIED state does not permanently block subsequent requests
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationAlwaysRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── v2.2.0 module split regressions ────────────────────────────────────

    @Test
    fun `v2_2 - GrantLocationAlways initialize is idempotent`() {
        GrantLocationAlways.initialize()
        GrantLocationAlways.initialize()
        GrantLocationAlways.initialize()
        // No assertion needed — absence of exception is the contract.
    }

    @Test
    fun `v2_2 - LOCATION_ALWAYS is distinct from foreground LOCATION`() {
        assertNotEquals(
            AppGrant.LOCATION.identifier,
            AppGrant.LOCATION_ALWAYS.identifier,
            "Always-location must stay a distinct permission after being moved to its own module"
        )
    }

    @Test
    fun `v2_2 - LOCATION_ALWAYS still requires background upgrade`() {
        assertTrue(
            AppGrant.LOCATION_ALWAYS.requiresBackgroundUpgrade,
            "Moving always-location to its own module must not drop the background-upgrade contract"
        )
    }

    @Test
    fun `v2_2 - handler requests only LOCATION_ALWAYS`() = testScope.runTest {
        manager.mockRequestResult = GrantStatus.GRANTED
        manager.mockStatus = GrantStatus.NOT_DETERMINED

        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, testScope)
        handler.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.LOCATION_ALWAYS, manager.requestedGrants[0],
            "Always-location handler must only request AppGrant.LOCATION_ALWAYS")
    }

    // ── state machine regressions ──────────────────────────────────────────

    @Test
    fun `DENIED state does not permanently block subsequent request`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, testScope)

        handler.request {}
        advanceUntilIdle()

        // Simulate user granting permission manually (e.g., in Settings)
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "DENIED state must not permanently block future requests")
    }

    @Test
    fun `onDismiss after DENIED clears dialog state without requesting again`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, testScope)

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
    fun `refreshStatus after GRANTED stays GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, testScope)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)

        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
