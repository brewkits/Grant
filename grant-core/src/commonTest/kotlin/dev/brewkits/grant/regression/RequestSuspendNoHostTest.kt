package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.PlatformConfig
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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
 * Regression for the 2.3.0 `requestSuspend` no-host hang (found via the Lam gallery P0,
 * 2026-07-09).
 *
 * Buggy behaviour
 * ---------------
 * `requestSuspend()` on a DENIED / DENIED_ALWAYS grant routed into the
 * [dev.brewkits.grant.UiStrategy.StateBased] rationale / settings-guide branches, which raise
 * dialog state and park the flow until the dialog host resumes it. When the app renders no
 * dialog for that handler (a headless `requestSuspend`, e.g. a background pre-check before a
 * screen loads), nothing could ever resume the flow — the caller suspended FOREVER. In Lam
 * this wedged the gallery's entire first load on a skeleton grid.
 *
 * Fix
 * ---
 * After `handleStatus` returns, `requestSuspend` detects "parked on a dialog with no collector
 * attached to [GrantHandler.state]" and completes with the current status instead, clearing
 * the unrenderable dialog state (mirroring `onDismiss`, including the rationale memory). The
 * callback-based `request()` flow is deliberately unchanged — raising dialog state without a
 * live collector is a supported pattern there.
 */
class RequestSuspendNoHostTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `requestSuspend with no host completes instead of hanging on DENIED_ALWAYS`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED_ALWAYS
            mockRequestResult = GrantStatus.DENIED_ALWAYS
        }
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        // No collector on handler.state — the pre-fix behaviour was an infinite suspend here,
        // which runTest would surface as an uncompleted-coroutine failure.
        val result = async { handler.requestSuspend() }
        advanceUntilIdle()

        assertTrue(result.isCompleted, "requestSuspend must not park when no dialog host exists")
        assertEquals(GrantStatus.DENIED_ALWAYS, result.await())
        assertFalse(handler.state.value.isVisible, "unrenderable dialog state must be cleared")
    }

    @Test
    fun `requestSuspend with no host completes on a soft DENIED and keeps rationale fresh`() = testScope.runTest {
        if (!PlatformConfig.isRationaleSupported) return@runTest

        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        val result = async { handler.requestSuspend() }
        advanceUntilIdle()

        assertTrue(result.isCompleted, "requestSuspend must not park when no dialog host exists")
        assertEquals(GrantStatus.DENIED, result.await())
        assertFalse(handler.state.value.isVisible)

        // The user never actually saw a rationale, so the memory must stay unset: a later
        // hosted request{} still explains itself with the rationale dialog first.
        handler.request { }
        advanceUntilIdle()
        assertTrue(
            handler.state.value.showRationale,
            "a later request must still surface the rationale — the no-host safeguard must not consume it"
        )
    }

    @Test
    fun `requestSuspend WITH a host still parks on the dialog and resumes via onDismiss`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED_ALWAYS
            mockRequestResult = GrantStatus.DENIED_ALWAYS
        }
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        // A live dialog host — what GrantDialog's collectAsState does in composition.
        val host = launch { handler.state.collect { } }
        advanceUntilIdle()

        val result = async { handler.requestSuspend() }
        advanceUntilIdle()

        assertFalse(result.isCompleted, "with a host attached the flow must park on the settings guide")
        assertTrue(handler.state.value.isVisible, "the settings guide must be visible for the host")
        assertTrue(handler.state.value.showSettingsGuide)

        handler.onDismiss()
        advanceUntilIdle()
        assertTrue(result.isCompleted, "onDismiss must resume the parked flow")

        host.cancelAndJoin()
    }

    @Test
    fun `requestSuspend with no host still returns GRANTED normally`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        val result = async { handler.requestSuspend() }
        advanceUntilIdle()

        assertTrue(result.isCompleted)
        assertEquals(GrantStatus.GRANTED, result.await())
    }
}
