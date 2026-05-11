package dev.brewkits.grant.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end system test for the LOCATION_ALWAYS lifecycle.
 *
 * Drives a [GrantHandler] through every state transition that a real user can
 * trigger for `AppGrant.LOCATION_ALWAYS`:
 *
 *   1. First open: status = NOT_DETERMINED
 *   2. User taps Request → OS prompt → user grants foreground only → PARTIAL_GRANTED
 *   3. Handler routes to settings dialog (Issue #33 fix)
 *   4. User dismisses dialog → state cleared
 *   5. User taps Request again → still PARTIAL_GRANTED → settings dialog reappears
 *   6. User taps "Open Settings" → openSettings invoked; state cleared
 *   7. (After user grants background in OS settings) status flips to GRANTED →
 *      next request fires onGranted callback
 *
 * Together with [dev.brewkits.grant.regression.Issue33PartialGrantedSettingsTest]
 * this guarantees the LOCATION_ALWAYS flow is correct end-to-end, not just in
 * isolated branches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationAlwaysFullJourneySystemTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `full LOCATION_ALWAYS journey from undetermined to granted`() = testScope.runTest {
        // Step 1: first open, status undetermined.
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var grantedCount = 0
        handler.request { grantedCount++ }
        advanceUntilIdle()

        // Step 2-3: PARTIAL_GRANTED returned → settings dialog must appear.
        // This is the Issue #33 fix path: requiresBackgroundUpgrade triggers
        // DENIED_ALWAYS routing with isFirstRequest=false, so settings shows.
        var s = handler.state.value
        assertTrue(s.isVisible, "settings dialog must be visible after PARTIAL_GRANTED on first tap")
        assertTrue(s.showSettingsGuide)
        assertFalse(s.showRationale)
        assertEquals(0, grantedCount, "onGranted must not fire while background is denied")

        // Step 4: user dismisses dialog.
        handler.onDismiss()
        advanceUntilIdle()
        s = handler.state.value
        assertFalse(s.isVisible)
        assertFalse(s.showSettingsGuide)

        // Step 5: user taps Request again — status is now PARTIAL_GRANTED on disk.
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        manager.mockRequestResult = GrantStatus.PARTIAL_GRANTED
        handler.request { grantedCount++ }
        advanceUntilIdle()

        s = handler.state.value
        assertTrue(s.showSettingsGuide, "settings dialog must reappear on second tap")
        assertEquals(0, grantedCount)

        // Step 6: user clicks "Open Settings".
        handler.onSettingsConfirmed()
        advanceUntilIdle()
        assertTrue(manager.openSettingsCalled, "GrantManager.openSettings must be invoked")
        s = handler.state.value
        assertFalse(s.isVisible)

        // Step 7: user grants background in OS settings → status flips to GRANTED.
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        handler.request { grantedCount++ }
        advanceUntilIdle()

        s = handler.state.value
        assertFalse(s.isVisible, "no dialog when fully granted")
        assertEquals(1, grantedCount, "onGranted must fire exactly once")
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    /**
     * Verifies that the LOCATION_ALWAYS-specific routing only triggers for
     * permissions whose `requiresBackgroundUpgrade` is true. For ordinary
     * partial-access permissions (e.g. GALLERY on Android 14), PARTIAL_GRANTED
     * remains a success state and onGranted fires.
     */
    @Test
    fun `GALLERY PARTIAL_GRANTED still fires onGranted - no false positive on Issue 33 routing`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        manager.mockRequestResult = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        var grantedCount = 0
        handler.request { grantedCount++ }
        advanceUntilIdle()

        assertEquals(1, grantedCount, "GALLERY PARTIAL_GRANTED must fire onGranted")
        assertFalse(handler.state.value.showSettingsGuide)
    }
}
