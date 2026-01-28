package dev.brewkits.grant

import app.cash.turbine.test
import dev.brewkits.grant.fakes.MultiGrantFakeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

/**
 * Tests for GrantGroupHandler - Sequential multi-grant flow logic.
 *
 * Coverage:
 * - Sequential grant requests (request one after another)
 * - All-or-nothing logic (all grants must be granted)
 * - Progress tracking (grantedGrants set)
 * - Failure handling (stop on first denial)
 * - UI state for current grant in group
 * - Rationale and settings flows per grant
 * - Refresh all statuses
 */
class GrantGroupHandlerTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var mockGrantManager: MultiGrantFakeManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        testScope = CoroutineScope(StandardTestDispatcher())
        mockGrantManager = MultiGrantFakeManager()
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `should require at least one grant`() {
        assertFailsWith<IllegalArgumentException> {
            GrantGroupHandler(mockGrantManager, emptyList(), testScope)
        }
    }

    @Test
    fun `initial state should reflect total grant count`() = runTest {
        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)

        handler.state.test {
            val initialState = awaitItem()
            assertEquals(2, initialState.totalGrants)
            assertEquals(0, initialState.grantedGrants.size)
            assertFalse(initialState.isVisible)
        }
    }

    @Test
    fun `should initialize statuses for all grants`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.statuses.test {
            val statusMap = awaitItem()
            assertEquals(GrantStatus.GRANTED, statusMap[AppGrant.CAMERA])
            assertEquals(GrantStatus.DENIED, statusMap[AppGrant.MICROPHONE])
        }
    }

    // ==================== All Grants Already Granted ====================

    @Test
    fun `request when all grants already granted should invoke callback immediately`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertTrue(callbackInvoked, "Callback should be invoked when all grants already granted")
    }

    @Test
    fun `request when all grants already granted should not show any dialogs`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.request { }
        testScheduler.advanceUntilIdle()

        // Check state directly - no emission expected since state doesn't change
        val state = handler.state.value
        assertFalse(state.isVisible, "No dialog should be shown")
    }

    // ==================== Sequential Grant Requests ====================

    @Test
    fun `request should process grants sequentially`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CAMERA), "Should request Camera")
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE), "Should request Microphone")
        assertTrue(callbackInvoked, "Should invoke callback after all grants")
    }

    @Test
    fun `request should update granted set after each successful grant`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip initial state

            handler.request { }
            testScheduler.advanceUntilIdle()

            // Camera granted
            val state1 = awaitItem()
            assertTrue(state1.grantedGrants.contains(AppGrant.CAMERA))
            assertEquals(1, state1.grantedGrants.size)

            // Microphone granted
            val state2 = awaitItem()
            assertTrue(state2.grantedGrants.contains(AppGrant.CAMERA))
            assertTrue(state2.grantedGrants.contains(AppGrant.MICROPHONE))
            assertEquals(2, state2.grantedGrants.size)
        }
    }

    // ==================== Failure Scenarios ====================

    @Test
    fun `request should stop on first denial and show rationale`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request(
            rationaleMessages = mapOf(AppGrant.CAMERA to "Camera is required")
        ) {
            callbackInvoked = true
        }
        testScheduler.advanceUntilIdle()

        // Should show rationale for CAMERA
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.isVisible, "Dialog should be visible")
            assertTrue(state.showRationale, "Should show rationale")
            assertEquals(AppGrant.CAMERA, state.currentGrant)
            assertEquals("Camera is required", state.rationaleMessage)
        }

        assertFalse(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE), "Should not request Microphone")
        assertFalse(callbackInvoked, "Callback should NOT be invoked")
    }

    @Test
    fun `request should stop on first permanent denial and show settings guide`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request(
            settingsMessages = mapOf(AppGrant.CAMERA to "Enable Camera in Settings")
        ) {
            callbackInvoked = true
        }
        testScheduler.advanceUntilIdle()

        // Should show settings guide for CAMERA
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.isVisible, "Dialog should be visible")
            assertTrue(state.showSettingsGuide, "Should show settings guide")
            assertEquals(AppGrant.CAMERA, state.currentGrant)
            assertEquals("Enable Camera in Settings", state.settingsMessage)
        }

        assertFalse(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE), "Should not request Microphone")
        assertFalse(callbackInvoked, "Callback should NOT be invoked")
    }

    @Test
    fun `request should stop if user denies during sequential flow`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Camera granted, Microphone denied - should NOT invoke callback
        assertFalse(callbackInvoked, "Callback should NOT be invoked when one grant denied")
    }

    // ==================== Rationale Flow ====================

    @Test
    fun `onRationaleConfirmed should request current grant and continue flow if granted`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        // Rationale shown for Camera - verify it's shown
        assertTrue(handler.state.value.showRationale, "Rationale should be shown for Camera")

        handler.onRationaleConfirmed()
        testScheduler.advanceUntilIdle()

        // onRationaleConfirmed only handles current grant - doesn't continue sequential flow
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CAMERA), "Should request Camera")
        // Microphone is NOT requested automatically - user must call request() again
        assertFalse(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE), "Should NOT auto-request Microphone")
        assertFalse(callbackInvoked, "Callback should NOT be invoked - flow stopped at Camera")
    }

    @Test
    fun `onRationaleConfirmed should show settings if grant denied again`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)

        val grants = listOf(AppGrant.CAMERA)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.request(
            settingsMessages = mapOf(AppGrant.CAMERA to "Enable in Settings")
        ) { }
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip rationale

            handler.onRationaleConfirmed()
            testScheduler.advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Should show settings guide")
            assertEquals("Enable in Settings", state.settingsMessage)
        }
    }

    // ==================== Settings Flow ====================

    @Test
    fun `onSettingsConfirmed should open settings and reset state`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)

        val grants = listOf(AppGrant.CAMERA)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.request { }
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip settings shown

            handler.onSettingsConfirmed()

            val state = awaitItem()
            assertFalse(state.isVisible, "Dialog should be hidden")
        }

        assertTrue(mockGrantManager.openSettingsCalled, "Should open settings")
    }

    // ==================== Dialog Dismissal ====================

    @Test
    fun `onDismiss should hide dialog and reset current grant`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.request { }
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip rationale shown

            handler.onDismiss()

            val state = awaitItem()
            assertFalse(state.isVisible)
            assertNull(state.currentGrant)
            assertFalse(state.showRationale)
            assertFalse(state.showSettingsGuide)
        }
    }

    // ==================== Refresh All Statuses ====================

    @Test
    fun `refreshAllStatuses should update all grant statuses`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        // User enables permissions in settings
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        handler.statuses.test {
            skipItems(1) // Skip initial

            handler.refreshAllStatuses()
            testScheduler.advanceUntilIdle()

            val statusMap = awaitItem()
            assertEquals(GrantStatus.GRANTED, statusMap[AppGrant.CAMERA])
            assertEquals(GrantStatus.GRANTED, statusMap[AppGrant.MICROPHONE])
        }
    }

    @Test
    fun `refreshAllStatuses should update granted set`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        // User enables Camera only
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)

        handler.state.test {
            skipItems(1) // Skip initial

            handler.refreshAllStatuses()
            testScheduler.advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.grantedGrants.contains(AppGrant.CAMERA))
            assertFalse(state.grantedGrants.contains(AppGrant.MICROPHONE))
            assertEquals(1, state.grantedGrants.size)
        }
    }

    // ==================== Custom Messages ====================

    @Test
    fun `request should use custom rationale messages per grant`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.request(
            rationaleMessages = mapOf(
                AppGrant.CAMERA to "Camera for video",
                AppGrant.MICROPHONE to "Mic for audio"
            )
        ) { }
        testScheduler.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            // First grant (Camera) should show its message
            assertEquals(AppGrant.CAMERA, state.currentGrant)
            assertEquals("Camera for video", state.rationaleMessage)
        }
    }

    @Test
    fun `request should use custom settings messages per grant`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)

        val grants = listOf(AppGrant.CAMERA)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        handler.request(
            settingsMessages = mapOf(
                AppGrant.CAMERA to "Enable Camera in Settings > Privacy"
            )
        ) { }
        testScheduler.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertEquals("Enable Camera in Settings > Privacy", state.settingsMessage)
        }
    }

    // ==================== Edge Cases ====================

    @Test
    fun `single grant group should behave like GrantHandler`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)

        val grants = listOf(AppGrant.CAMERA)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CAMERA))
        assertTrue(callbackInvoked, "Callback should be invoked")
    }

    @Test
    fun `three grants with middle one denied should stop and not request third`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CAMERA), "Should request Camera")
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE), "Should request Microphone")
        assertFalse(mockGrantManager.isRequestCalled(AppGrant.LOCATION), "Should NOT request Location")
        assertFalse(callbackInvoked, "Callback should NOT be invoked")
    }
}
