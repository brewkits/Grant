package dev.brewkits.grant.motion

import dev.brewkits.grant.AppGrant
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GrantMotionTest {

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
        GrantMotion.initialize()
    }

    @Test
    fun initialize_isIdempotent() {
        GrantMotion.initialize()
        GrantMotion.initialize()
        GrantMotion.initialize()
    }

    // ── AppGrant identifier ────────────────────────────────────────────────

    @Test
    fun `MOTION identifier is non-empty`() {
        assertTrue(AppGrant.MOTION.identifier.isNotEmpty())
    }

    @Test
    fun `MOTION identifier is distinct from all other AppGrant values`() {
        val others = AppGrant.values().filter { it != AppGrant.MOTION }
        others.forEach { other ->
            assertNotEquals(AppGrant.MOTION.identifier, other.identifier,
                "MOTION identifier must not collide with ${other.name}")
        }
    }

    @Test
    fun `MOTION requiresBackgroundUpgrade is false`() {
        assertFalse(AppGrant.MOTION.requiresBackgroundUpgrade)
    }

    // ── GrantHandler basics ────────────────────────────────────────────────

    @Test
    fun `MOTION handler initial status is NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `request MOTION when GRANTED invokes callback without re-requesting`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        var called = false
        handler.request { called = true }
        advanceUntilIdle()

        assertTrue(called)
        assertFalse(manager.requestCalled, "Already GRANTED — no system dialog should be triggered")
    }

    @Test
    fun `MOTION does not return PARTIAL_GRANTED for any valid status`() = testScope.runTest {
        val validStatuses = listOf(
            GrantStatus.NOT_DETERMINED,
            GrantStatus.GRANTED,
            GrantStatus.DENIED,
            GrantStatus.DENIED_ALWAYS
        )

        for (status in validStatuses) {
            manager.mockStatus = status
            val handler = GrantHandler(manager, AppGrant.MOTION, testScope)
            handler.refreshStatus()
            advanceUntilIdle()
            assertNotEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value,
                "Motion must never return PARTIAL_GRANTED — hardware is all-or-nothing")
        }
    }

    @Test
    fun `DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        handler.request(settingsMessage = "Motion activity unavailable") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide)
        assertFalse(handler.state.value.showRationale)
    }

    @Test
    fun `refreshStatus updates status from manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.DENIED, handler.status.value)
    }

    @Test
    fun `onDismiss hides dialog without triggering new request`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.MOTION, this)

        handler.request {}
        advanceUntilIdle()
        val countBefore = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        assertFalse(handler.state.value.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(countBefore, manager.requestedGrants.size, "Dismiss must not trigger another request")
    }

    @Test
    fun `requestSuspend GRANTED returns GRANTED status`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, this)
        val result = handler.requestSuspend()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, result)
    }
}
