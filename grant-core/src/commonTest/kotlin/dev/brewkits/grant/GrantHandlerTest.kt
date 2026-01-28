package dev.brewkits.grant

import app.cash.turbine.test
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

/**
 * Tests for GrantHandler - Complex state machine with StateFlow.
 *
 * Coverage:
 * - State transitions (NOT_DETERMINED → GRANTED/DENIED/DENIED_ALWAYS)
 * - UI state updates (showRationale, showSettingsGuide)
 * - Callback invocations and memory leak prevention
 * - Rationale and settings confirmation flows
 * - Refresh status functionality
 * - Custom UI flow with callbacks
 */
class GrantHandlerTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var mockGrantManager: FakeGrantManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        testScope = CoroutineScope(StandardTestDispatcher())
        mockGrantManager = FakeGrantManager()
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial status should be loaded from GrantManager`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)

        // Wait for initialization
        testScheduler.advanceUntilIdle()

        handler.status.test {
            assertEquals(GrantStatus.GRANTED, awaitItem())
        }
    }

    @Test
    fun `initial UI state should be hidden`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)

        handler.state.test {
            val initialState = awaitItem()
            assertFalse(initialState.isVisible)
            assertFalse(initialState.showRationale)
            assertFalse(initialState.showSettingsGuide)
        }
    }

    // ==================== Request Flow - Already Granted ====================

    @Test
    fun `request when already GRANTED should invoke callback immediately`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertTrue(callbackInvoked, "Callback should be invoked when already granted")
    }

    @Test
    fun `request when already GRANTED should not show any dialogs`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.request { }
        testScheduler.advanceUntilIdle()

        // Check state directly - no emission expected since state doesn't change
        val state = handler.state.value
        assertFalse(state.isVisible, "No dialog should be visible")
        assertFalse(state.showRationale)
        assertFalse(state.showSettingsGuide)
    }

    // ==================== Request Flow - NOT_DETERMINED ====================

    @Test
    fun `request when NOT_DETERMINED should request grant and invoke callback if granted`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.requestCalled, "GrantManager.request should be called")
        assertTrue(callbackInvoked, "Callback should be invoked after grant")
    }

    @Test
    fun `request when NOT_DETERMINED and denied should not invoke callback`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        assertFalse(callbackInvoked, "Callback should NOT be invoked when denied")
    }

    @Test
    fun `request when NOT_DETERMINED and then DENIED should not show rationale immediately`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.request(rationaleMessage = "Camera needed") { }
        testScheduler.advanceUntilIdle()

        // Check state directly - no emission expected since state doesn't change
        val state = handler.state.value
        // Should NOT show rationale after first system denial (isFirstRequest = true)
        assertFalse(state.isVisible, "Dialog should not be shown after first system denial")
        assertFalse(state.showRationale)
    }

    // ==================== Request Flow - DENIED (Soft Denial) ====================

    @Test
    fun `request when DENIED should show rationale dialog`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip initial

            handler.request(rationaleMessage = "Camera is required") { }
            testScheduler.advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.isVisible, "Dialog should be visible")
            assertTrue(state.showRationale, "Should show rationale")
            assertFalse(state.showSettingsGuide)
            assertEquals("Camera is required", state.rationaleMessage)
        }
    }

    @Test
    fun `onRationaleConfirmed should request grant again`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        mockGrantManager.requestCalled = false // Reset
        handler.onRationaleConfirmed()
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.requestCalled, "Should request grant again")
        assertTrue(callbackInvoked, "Callback should be invoked after grant")
    }

    @Test
    fun `onRationaleConfirmed when denied again should hide dialog`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.request { }
        testScheduler.advanceUntilIdle()

        handler.onRationaleConfirmed()
        testScheduler.advanceUntilIdle()

        // Check state directly - should now show settings guide
        val state = handler.state.value
        assertTrue(state.isVisible)
        assertFalse(state.showRationale)
        assertTrue(state.showSettingsGuide, "Should show settings guide after rationale")
    }

    // ==================== Request Flow - DENIED_ALWAYS (Hard Denial) ====================

    @Test
    fun `request when DENIED_ALWAYS should show settings guide if rationale was shown before`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip initial

            // First request - shows rationale
            handler.request(rationaleMessage = "Camera needed", settingsMessage = "Enable in Settings") { }
            testScheduler.advanceUntilIdle()

            val rationaleState = awaitItem()
            assertTrue(rationaleState.showRationale, "Should show rationale first")

            // Simulate user denying permanently
            mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
            mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
            handler.onRationaleConfirmed()
            testScheduler.advanceUntilIdle()

            // onRationaleConfirmed() emits resetState() first, then settings guide
            val resetState = awaitItem()
            assertFalse(resetState.isVisible, "Dialog is hidden during reset")

            val settingsState = awaitItem()
            assertTrue(settingsState.isVisible, "Dialog should be visible")
            assertFalse(settingsState.showRationale)
            assertTrue(settingsState.showSettingsGuide, "Should show settings guide")
            // onRationaleConfirmed() doesn't preserve messages - it passes null
            assertNull(settingsState.settingsMessage, "Message is not preserved after onRationaleConfirmed")
        }
    }

    @Test
    fun `request when DENIED_ALWAYS without prior rationale should not show settings immediately`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.request(settingsMessage = "Enable in Settings") { }
        testScheduler.advanceUntilIdle()

        // Check state directly - no emission expected since state doesn't change
        val state = handler.state.value
        // Should NOT show settings after first system denial
        assertFalse(state.isVisible, "Should not show settings guide immediately")
        assertFalse(state.showSettingsGuide)
    }

    @Test
    fun `request when DENIED_ALWAYS on second request should show settings guide`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1) // Skip initial

            // Second request (isFirstRequest = false)
            handler.request(settingsMessage = "Enable in Settings") { }
            testScheduler.advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.isVisible, "Dialog should be visible on second request")
            assertTrue(state.showSettingsGuide, "Should show settings guide")
            assertEquals("Enable in Settings", state.settingsMessage)
        }
    }

    @Test
    fun `onSettingsConfirmed should open settings and reset state`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.request { }
        testScheduler.advanceUntilIdle()

        handler.onSettingsConfirmed()
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.openSettingsCalled, "Should open settings")

        // Check state directly
        val state = handler.state.value
        assertFalse(state.isVisible, "Dialog should be hidden")
    }

    // ==================== Dialog Dismissal ====================

    @Test
    fun `onDismiss should hide dialog and clear callback`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScheduler.advanceUntilIdle()

        handler.onDismiss()
        testScheduler.advanceUntilIdle()

        // Check state directly
        val state = handler.state.value
        assertFalse(state.isVisible, "Dialog should be hidden")
        assertFalse(state.showRationale)
        assertFalse(state.showSettingsGuide)

        // Verify callback was cleared (no way to invoke it now)
        assertFalse(callbackInvoked, "Callback should not be invoked after dismiss")
    }

    // ==================== Refresh Status ====================

    @Test
    fun `refreshStatus should update status flow`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.status.test {
            skipItems(1) // Skip initial DENIED

            // User enabled permission in settings
            mockGrantManager.mockStatus = GrantStatus.GRANTED
            handler.refreshStatus()
            testScheduler.advanceUntilIdle()

            assertEquals(GrantStatus.GRANTED, awaitItem())
        }
    }

    // ==================== Memory Leak Prevention ====================

    @Test
    fun `callback should be cleared after invocation to prevent memory leak`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var invocationCount = 0
        handler.request { invocationCount++ }
        testScheduler.advanceUntilIdle()

        assertEquals(1, invocationCount, "Callback should be invoked once")

        // Manually try to trigger again (simulating retained handler)
        // Callback should be cleared, so nothing happens
        handler.refreshStatus()
        testScheduler.advanceUntilIdle()

        assertEquals(1, invocationCount, "Callback should still be 1 (was cleared)")
    }

    // ==================== Custom UI Flow ====================

    @Test
    fun `requestWithCustomUi should invoke rationale callback when DENIED`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var rationaleShown = false
        var rationaleMessage = ""

        handler.requestWithCustomUi(
            rationaleMessage = "Camera is required",
            onShowRationale = { message, _, _ ->
                rationaleShown = true
                rationaleMessage = message
            },
            onShowSettings = { _, _, _ -> },
            onGranted = { }
        )
        testScheduler.advanceUntilIdle()

        assertTrue(rationaleShown, "Rationale callback should be invoked")
        assertEquals("Camera is required", rationaleMessage)
    }

    @Test
    fun `requestWithCustomUi should invoke settings callback when DENIED_ALWAYS`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var settingsShown = false
        var settingsMessage = ""

        handler.requestWithCustomUi(
            settingsMessage = "Enable in Settings",
            onShowRationale = { _, _, _ -> },
            onShowSettings = { message, _, _ ->
                settingsShown = true
                settingsMessage = message
            },
            onGranted = { }
        )
        testScheduler.advanceUntilIdle()

        assertTrue(settingsShown, "Settings callback should be invoked")
        assertEquals("Enable in Settings", settingsMessage)
    }

    @Test
    fun `requestWithCustomUi rationale onConfirm should request grant`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var grantedInvoked = false
        var onConfirm: (() -> Unit)? = null

        handler.requestWithCustomUi(
            onShowRationale = { _, confirm, _ -> onConfirm = confirm },
            onShowSettings = { _, _, _ -> },
            onGranted = { grantedInvoked = true }
        )
        testScheduler.advanceUntilIdle()

        assertNotNull(onConfirm, "onConfirm should be captured")
        mockGrantManager.requestCalled = false
        onConfirm?.invoke()
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.requestCalled, "Should request grant")
        assertTrue(grantedInvoked, "onGranted should be invoked")
    }

    @Test
    fun `requestWithCustomUi settings onConfirm should open settings`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var onConfirm: (() -> Unit)? = null

        handler.requestWithCustomUi(
            onShowRationale = { _, _, _ -> },
            onShowSettings = { _, confirm, _ -> onConfirm = confirm },
            onGranted = { }
        )
        testScheduler.advanceUntilIdle()

        assertNotNull(onConfirm)
        onConfirm?.invoke()
        testScheduler.advanceUntilIdle()

        assertTrue(mockGrantManager.openSettingsCalled, "Should open settings")
    }

    @Test
    fun `requestWithCustomUi onDismiss should clear callback`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var grantedInvoked = false
        var onDismiss: (() -> Unit)? = null

        handler.requestWithCustomUi(
            onShowRationale = { _, _, dismiss -> onDismiss = dismiss },
            onShowSettings = { _, _, _ -> },
            onGranted = { grantedInvoked = true }
        )
        testScheduler.advanceUntilIdle()

        assertNotNull(onDismiss)
        onDismiss?.invoke()
        testScheduler.advanceUntilIdle()

        assertFalse(grantedInvoked, "Callback should be cleared after dismiss")
    }

    // ==================== Edge Cases ====================

    @Test
    fun `request with null messages should use empty UI state`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        handler.state.test {
            skipItems(1)

            handler.request(rationaleMessage = null) { }
            testScheduler.advanceUntilIdle()

            val state = awaitItem()
            assertNull(state.rationaleMessage, "Message should be null")
        }
    }

    @Test
    fun `multiple rapid requests should only invoke callback once`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        testScheduler.advanceUntilIdle()

        var invocationCount = 0

        handler.request { invocationCount++ }
        handler.request { invocationCount++ } // Second request
        handler.request { invocationCount++ } // Third request
        testScheduler.advanceUntilIdle()

        // Last callback wins (overwrites previous)
        assertEquals(1, invocationCount, "Only last callback should be invoked once")
    }
}
