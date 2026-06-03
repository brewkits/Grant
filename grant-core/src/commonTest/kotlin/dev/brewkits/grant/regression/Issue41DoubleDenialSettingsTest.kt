package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.PlatformConfig
import dev.brewkits.grant.fakes.FakeGrantManager
import dev.brewkits.grant.fakes.MultiGrantFakeManager
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
 * Regression for Issue #41: "'Open Settings' is still not possible if permission has
 * been denied twice."
 *
 * Reproduction (Android, Pixel 8 / API 36)
 * ----------------------------------------
 * The user denies a permission, sees the rationale dialog, taps "Continue", and the OS
 * denies again (for blocked / background permissions the OS resolves immediately without
 * showing UI, often mapping the result back to `DENIED` rather than `DENIED_ALWAYS`).
 *
 * Buggy behaviour
 * ---------------
 * - `GrantHandler`: after the second `DENIED`, the flow only `resetState()` + cleared
 *   callbacks, so the rationale's "Continue" button appeared to "do nothing" — a UX
 *   dead-end with no path to Settings.
 * - `GrantGroupHandler`: it had no memory of having already shown the rationale, so it
 *   re-emitted `showRationale = true` on every `DENIED`, producing an infinite loop.
 *
 * Fix
 * ---
 * Once a rationale has been shown for a permission and the OS still reports `DENIED`,
 * both handlers escalate to the settings guide (`showSettingsGuide = true`) instead of
 * looping or dead-ending. `GrantHandler` tracks this with `hasShownRationaleDialog`;
 * `GrantGroupHandler` tracks it with the `shownRationaleGrants` set. Both reset that
 * memory in `onDismiss()` so a fresh attempt explains the rationale again.
 */
class Issue41DoubleDenialSettingsTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `Issue-41 - GrantHandler double denial escalates rationale to settings guide`() = testScope.runTest {
        // Already denied once; the OS keeps returning DENIED on every re-request.
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        var granted = 0
        handler.request { granted++ }
        advanceUntilIdle()

        if (PlatformConfig.isRationaleSupported) {
            // Android: a soft-denial first surfaces the rationale dialog.
            assertTrue(handler.state.value.showRationale, "rationale must be shown after the first denial")
            assertFalse(handler.state.value.showSettingsGuide)

            // User taps "Continue"; the OS denies a second time.
            handler.onRationaleConfirmed()
            advanceUntilIdle()

            // The bug: this used to dead-end (nothing visible). The fix escalates to settings.
            assertTrue(
                handler.state.value.showSettingsGuide,
                "settings guide MUST appear after the second denial (Issue #41)"
            )
            assertFalse(handler.state.value.showRationale, "must NOT loop back to the rationale dialog")
            assertTrue(handler.state.value.isVisible, "dialog must stay visible — not a UX dead-end")
        } else {
            // iOS has no soft-denial state, so a DENIED routes straight to settings.
            assertTrue(handler.state.value.showSettingsGuide, "settings guide must be visible on iOS for DENIED")
        }

        assertEquals(0, granted, "onGranted must never fire while the permission stays denied")
    }

    @Test
    fun `Issue-41 - GrantHandler onDismiss resets rationale memory for a fresh attempt`() = testScope.runTest {
        if (!PlatformConfig.isRationaleSupported) return@runTest

        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        // First attempt shows the rationale, then the user dismisses it.
        handler.request { }
        advanceUntilIdle()
        assertTrue(handler.state.value.showRationale)

        handler.onDismiss()
        advanceUntilIdle()
        assertFalse(handler.state.value.isVisible)

        // A brand-new attempt must explain the rationale again (memory was reset),
        // NOT jump straight to the settings guide.
        handler.request { }
        advanceUntilIdle()
        assertTrue(handler.state.value.showRationale, "rationale must be shown again after a dismiss")
        assertFalse(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `Issue-41 - requestWithCustomUi double denial surfaces settings guidance`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        var rationaleShown = 0
        var settingsShown = 0
        var granted = 0

        handler.requestWithCustomUi(
            onShowRationale = { _, onConfirm, _ ->
                rationaleShown++
                onConfirm() // user taps "Continue"; the OS will deny again
            },
            onShowSettings = { _, _, onDismiss ->
                settingsShown++
                onDismiss() // close without opening settings, just assert it was offered
            },
            onGranted = { granted++ }
        )
        advanceUntilIdle()

        if (PlatformConfig.isRationaleSupported) {
            // Android: rationale offered once, then the OS re-denies → settings guidance.
            assertEquals(1, rationaleShown, "rationale must be offered exactly once on Android")
        } else {
            // iOS: no soft-denial rationale — straight to settings guidance.
            assertEquals(0, rationaleShown, "rationale must NOT be shown on iOS")
        }
        assertEquals(1, settingsShown, "settings guidance MUST be offered after denial (Issue #41)")
        assertEquals(0, granted, "onGranted must not fire while denied")
    }

    @Test
    fun `Issue-41 - GrantGroupHandler double denial does not loop and shows settings guide`() = testScope.runTest {
        // A single permission in the group that stays denied across requests.
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
            setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED)
        }
        val group = GrantGroupHandler(manager, listOf(AppGrant.CAMERA), this)

        var allGranted = 0
        group.request(onAllGranted = { allGranted++ })
        advanceUntilIdle()

        // First denial surfaces the rationale for the offending grant.
        assertTrue(group.state.value.showRationale, "group must show the rationale on the first denial")
        assertEquals(AppGrant.CAMERA, group.state.value.currentGrant)
        assertFalse(group.state.value.showSettingsGuide)

        // User taps "Continue"; the OS denies again.
        group.onRationaleConfirmed()
        advanceUntilIdle()

        // The bug: the group used to re-emit showRationale forever (infinite loop).
        assertTrue(
            group.state.value.showSettingsGuide,
            "group MUST escalate to the settings guide after the second denial (Issue #41)"
        )
        assertFalse(group.state.value.showRationale, "group must NOT loop back to the rationale dialog")
        assertEquals(0, allGranted, "onAllGranted must not fire while a permission stays denied")
    }

    @Test
    fun `Issue-41 - rationale memory resets after a grant-via-settings completion`() = testScope.runTest {
        if (!PlatformConfig.isRationaleSupported) return@runTest

        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        // Show the rationale, confirm, OS re-denies → settings guide (rationale memory set).
        handler.request { }
        advanceUntilIdle()
        assertTrue(handler.state.value.showRationale)
        handler.onRationaleConfirmed()
        advanceUntilIdle()
        assertTrue(handler.state.value.showSettingsGuide)

        // User grants the permission in Settings and returns → refreshStatus completes.
        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()
        assertFalse(handler.state.value.isVisible, "flow completes once granted via settings")

        // The permission is later revoked; a fresh attempt must explain the rationale
        // AGAIN (memory was reset), not jump straight to the settings guide.
        manager.mockStatus = GrantStatus.DENIED
        handler.request { }
        advanceUntilIdle()
        assertTrue(
            handler.state.value.showRationale,
            "rationale memory must reset after a grant-via-settings completion"
        )
        assertFalse(handler.state.value.showSettingsGuide)
    }
}
