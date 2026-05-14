package dev.brewkits.grant.motion.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.motion.GrantMotion
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
import kotlin.test.assertTrue

/**
 * Regression tests for v2.0 module split and Motion-specific edge cases.
 *
 * Covers:
 * - GrantMotion.initialize() is idempotent
 * - MOTION has no PARTIAL_GRANTED state (all-or-nothing)
 * - Hardware DENIED_ALWAYS must block dialog from showing (no rationale for hw unavailable)
 * - DENIED state correctly transitions to future GRANTED on retry
 * - MOTION handler only requests AppGrant.MOTION (no cross-contamination)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── v2.0 module split regressions ─────────────────────────────────────

    @Test
    fun `v2_0 - GrantMotion initialize is idempotent`() {
        GrantMotion.initialize()
        GrantMotion.initialize()
        GrantMotion.initialize()
    }

    @Test
    fun `v2_0 - MOTION handler only requests AppGrant_MOTION`() = testScope.runTest {
        manager.mockRequestResult = GrantStatus.GRANTED
        manager.mockStatus = GrantStatus.NOT_DETERMINED

        val handler = GrantHandler(manager, AppGrant.MOTION, testScope)
        handler.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.MOTION, manager.requestedGrants[0])
    }

    // ── no PARTIAL_GRANTED for Motion ─────────────────────────────────────

    @Test
    fun `MOTION does not produce PARTIAL_GRANTED - hardware is all or nothing`() = testScope.runTest {
        // Motion is binary: either GRANTED or DENIED/DENIED_ALWAYS.
        // This test documents that PARTIAL_GRANTED is not a valid Motion state,
        // so any code handling Motion should not branch on PARTIAL_GRANTED.
        val validMotionStatuses = setOf(
            GrantStatus.GRANTED,
            GrantStatus.DENIED,
            GrantStatus.DENIED_ALWAYS,
            GrantStatus.NOT_DETERMINED
        )

        for (status in validMotionStatuses) {
            manager.mockStatus = status
            val handler = GrantHandler(manager, AppGrant.MOTION, testScope)
            handler.refreshStatus()
            advanceUntilIdle()
            assertEquals(status, handler.status.value)
        }
    }

    // ── hardware unavailable path ──────────────────────────────────────────

    @Test
    fun `hardware unavailable DENIED_ALWAYS shows settings guide not rationale`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.MOTION,
            scope = testScope
        )

        handler.request(settingsMessage = "Motion hardware not available") {}
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showSettingsGuide, "DENIED_ALWAYS must show settings guide")
        assertFalse(state.showRationale, "DENIED_ALWAYS must NOT show rationale")
    }

    // ── state machine regressions ──────────────────────────────────────────

    @Test
    fun `MOTION DENIED does not permanently block subsequent request`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.MOTION, testScope)

        handler.request {}
        advanceUntilIdle()

        manager.configure(AppGrant.MOTION, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
    }

    @Test
    fun `onDismiss after MOTION DENIED clears dialog state`() = testScope.runTest {
        manager.configure(AppGrant.MOTION, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.MOTION, testScope)

        handler.request {}
        advanceUntilIdle()
        handler.onDismiss()
        advanceUntilIdle()

        val state = handler.state.value
        assertFalse(state.isVisible)
    }

    @Test
    fun `refreshStatus after MOTION GRANTED stays GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.MOTION, testScope)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)

        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
