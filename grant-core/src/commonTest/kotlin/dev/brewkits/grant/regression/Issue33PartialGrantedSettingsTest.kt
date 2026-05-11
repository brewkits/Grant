package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
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
 * Regression for Issue #33: `LOCATION_ALWAYS` returning `PARTIAL_GRANTED` was treated
 * identically to `GRANTED`. The user was never routed to Settings to enable
 * background ("Always Allow") location.
 *
 * Fix (v1.4.0)
 * ------------
 * `GrantPermission.requiresBackgroundUpgrade` (true for `AppGrant.LOCATION_ALWAYS`)
 * causes both `GrantHandler` and `GrantGroupHandler` to route `PARTIAL_GRANTED` to
 * the settings guide instead of completing the flow as a success.
 *
 * For permissions that do NOT require a background upgrade (e.g. `GALLERY` partial
 * media access on Android 14), `PARTIAL_GRANTED` continues to be treated as a
 * legitimate success state.
 */
class Issue33PartialGrantedSettingsTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `Issue-33 - LOCATION_ALWAYS PARTIAL_GRANTED on second tap shows settings dialog`() = testScope.runTest {
        // Foreground was granted previously; background is still missing.
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.PARTIAL_GRANTED
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var granted = 0
        handler.request { granted++ }
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showSettingsGuide, "settings dialog must be visible for PARTIAL_GRANTED LOCATION_ALWAYS")
        assertFalse(state.showRationale)
        assertEquals(0, granted, "onGranted must NOT fire when background is still missing")
    }

    @Test
    fun `Issue-33 - LOCATION_ALWAYS first tap PARTIAL_GRANTED after request shows settings dialog`() = testScope.runTest {
        // First-tap flow: checkStatus returns NOT_DETERMINED, OS prompt resolves to PARTIAL_GRANTED
        // (user granted foreground, denied background). We must still show settings.
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var granted = 0
        handler.request { granted++ }
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(
            state.showSettingsGuide,
            "settings dialog must be visible on first tap when background is denied"
        )
        assertEquals(0, granted, "onGranted must NOT fire when background is denied on first tap")
    }

    @Test
    fun `Issue-33 - GALLERY PARTIAL_GRANTED still counts as success`() = testScope.runTest {
        // Sanity check: partial media access on Android 14 must NOT show settings.
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.PARTIAL_GRANTED
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        var granted = 0
        handler.request { granted++ }
        advanceUntilIdle()

        assertEquals(1, granted, "onGranted must fire for non-background permissions")
        assertFalse(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `Issue-33 - GrantGroupHandler with LOCATION_ALWAYS PARTIAL_GRANTED blocks onAllGranted`() = testScope.runTest {
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.LOCATION_ALWAYS, GrantStatus.PARTIAL_GRANTED)
            setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
            setRequestResult(AppGrant.LOCATION_ALWAYS, GrantStatus.PARTIAL_GRANTED)
            setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        }
        val group = GrantGroupHandler(
            manager,
            listOf(AppGrant.LOCATION_ALWAYS, AppGrant.CAMERA),
            this
        )

        var allGranted = 0
        group.request(onAllGranted = { allGranted++ })
        advanceUntilIdle()

        assertEquals(0, allGranted, "Group success must not fire while LOCATION_ALWAYS is PARTIAL_GRANTED")
        val state = group.state.value
        assertTrue(state.showSettingsGuide, "Group must surface settings dialog for LOCATION_ALWAYS")
        assertEquals(AppGrant.LOCATION_ALWAYS, state.currentGrant)
    }
}
