package dev.brewkits.grant.location

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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the grant-location-always module surface: the [GrantLocationAlways]
 * initializer and the [AppGrant.LOCATION_ALWAYS] permission contract, plus basic
 * [GrantHandler] wiring against a fake manager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantLocationAlwaysTest {

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
        GrantLocationAlways.initialize()
    }

    @Test
    fun initialize_isIdempotent() {
        GrantLocationAlways.initialize()
        GrantLocationAlways.initialize()
        GrantLocationAlways.initialize()
    }

    // ── AppGrant.LOCATION_ALWAYS contract ──────────────────────────────────

    @Test
    fun `LOCATION_ALWAYS identifier is non-empty`() {
        assertTrue(AppGrant.LOCATION_ALWAYS.identifier.isNotEmpty())
    }

    @Test
    fun `LOCATION_ALWAYS is distinct from foreground LOCATION`() {
        assertNotEquals(
            AppGrant.LOCATION.identifier,
            AppGrant.LOCATION_ALWAYS.identifier,
            "Background (always) location must be a distinct permission from foreground location"
        )
    }

    @Test
    fun `LOCATION_ALWAYS requiresBackgroundUpgrade is true`() {
        assertTrue(
            AppGrant.LOCATION_ALWAYS.requiresBackgroundUpgrade,
            "Always-location is a background permission and must require the background upgrade step"
        )
    }

    @Test
    fun `foreground LOCATION does not require background upgrade`() {
        assertFalse(AppGrant.LOCATION.requiresBackgroundUpgrade)
    }

    // ── GrantHandler basics ────────────────────────────────────────────────

    @Test
    fun `handler initial status reflects manager NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `request when GRANTED invokes callback without re-requesting`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var called = false
        handler.request { called = true }
        advanceUntilIdle()

        assertTrue(called)
        assertFalse(manager.requestCalled, "Already GRANTED — no system dialog should be triggered")
    }

    @Test
    fun `request NOT_DETERMINED forwards exactly LOCATION_ALWAYS to the manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        handler.request {}
        advanceUntilIdle()

        assertTrue(manager.requestCalled)
        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.LOCATION_ALWAYS, manager.requestedGrants.single())
    }

    @Test
    fun `request DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        handler.request(settingsMessage = "Enable Always location in Settings") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `refreshStatus updates status from manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.DENIED, handler.status.value)
    }

    @Test
    fun `onDismiss hides dialog without triggering new request`() = testScope.runTest {
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        handler.request {}
        advanceUntilIdle()
        val countBefore = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        assertFalse(handler.state.value.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(countBefore, manager.requestedGrants.size, "Dismiss must not trigger another request")
    }
}
