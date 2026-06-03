package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
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
 * Regression for an Issue #33 follow-up found during on-device testing (Pixel 6 Pro,
 * API 36): `GrantHandler.refreshStatus()` treated `PARTIAL_GRANTED` as success
 * unconditionally, ignoring [dev.brewkits.grant.GrantPermission.requiresBackgroundUpgrade].
 *
 * Why it mattered
 * ---------------
 * `GrantDialog` calls `refreshStatus()` on every `ON_RESUME`. For `LOCATION_ALWAYS`,
 * after the user grants foreground but denies background on the system settings page,
 * returning to the app fired `ON_RESUME` → `refreshStatus()`, which saw
 * `PARTIAL_GRANTED`, dismissed the settings guide, and fired `onGranted` — a false
 * success. The user was left at "Partial" with no path to enable background access,
 * contradicting Issue #33.
 *
 * Fix
 * ---
 * `refreshStatus()` (and the process-death recovery in `init`) now use `isSatisfied()`,
 * which mirrors the `handleStatus` PARTIAL_GRANTED branch: `PARTIAL_GRANTED` is success
 * only when the grant does NOT require a background upgrade.
 */
class Issue33RefreshStatusPartialTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `Issue-33 - refreshStatus keeps settings guide for PARTIAL LOCATION_ALWAYS`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.PARTIAL_GRANTED
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var granted = 0
        handler.request { granted++ }
        advanceUntilIdle()
        assertTrue(
            handler.state.value.showSettingsGuide,
            "precondition: settings guide must be visible for PARTIAL LOCATION_ALWAYS"
        )

        // Simulate returning from the system background-location settings page (ON_RESUME).
        // Background is still missing → checkStatus stays PARTIAL_GRANTED.
        handler.refreshStatus()
        advanceUntilIdle()

        assertTrue(
            handler.state.value.showSettingsGuide,
            "settings guide MUST remain — PARTIAL is not satisfied for LOCATION_ALWAYS"
        )
        assertTrue(handler.state.value.isVisible, "dialog must stay visible (no dead-end)")
        assertEquals(0, granted, "onGranted must NOT fire for PARTIAL LOCATION_ALWAYS on refresh")
    }

    @Test
    fun `Issue-33 - refreshStatus still completes for PARTIAL non-background grant`() = testScope.runTest {
        // GALLERY does NOT require a background upgrade, so PARTIAL_GRANTED is a
        // legitimate success and refreshStatus should complete the flow.
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        var granted = 0
        handler.request { granted++ }
        advanceUntilIdle()
        assertTrue(handler.state.value.isVisible, "precondition: a dialog is showing after denial")

        // User grants partial media access in Settings, then returns (ON_RESUME).
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertFalse(
            handler.state.value.isVisible,
            "dialog must dismiss once GALLERY is partially granted (success)"
        )
        assertEquals(1, granted, "onGranted must fire for PARTIAL GALLERY (no background upgrade)")
    }
}
