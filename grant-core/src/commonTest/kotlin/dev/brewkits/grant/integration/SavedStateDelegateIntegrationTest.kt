package dev.brewkits.grant.integration

import app.cash.turbine.test
import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for SavedStateDelegate with GrantHandler.
 *
 * Tests process death recovery scenarios where dialog state must be
 * preserved and restored (critical for Android).
 *
 * Scenarios tested:
 * - State persistence across process death
 * - Dialog state restoration
 * - NoOpSavedStateDelegate behavior
 * - State clearing
 * - Multiple save/restore cycles
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedStateDelegateIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    // ==================== Fake SavedStateDelegate Implementation ====================

    /**
     * Fake implementation that simulates Android SavedStateHandle.
     * Stores state in memory for testing.
     */
    class FakeSavedStateDelegate : SavedStateDelegate {
        private val storage = mutableMapOf<String, String>()

        override fun saveState(key: String, value: String) {
            storage[key] = value
        }

        override fun restoreState(key: String): String? {
            return storage[key]
        }

        override fun clear(key: String) {
            storage.remove(key)
        }

        fun clearAll() {
            storage.clear()
        }

        fun getStorageSize(): Int = storage.size
    }

    // ==================== Process Death Recovery Tests ====================

    @Test
    fun `Process death Dialog state is saved and restored`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        // Create handler with SavedStateDelegate
        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // User requests permission, gets rationale dialog
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale, "Rationale should be shown")
        }

        // Verify: State saved to delegate
        assertTrue(savedStateDelegate.getStorageSize() > 0, "State should be saved")

        // SIMULATE PROCESS DEATH - Create new handler with same delegate
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: State restored - dialog still shown
        restoredHandler.state.test {
            val restoredState = awaitItem()
            assertTrue(restoredState.showRationale, "Rationale should be restored after process death")
        }
    }

    @Test
    fun `Process death Settings guide state is restored`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Show settings guide
        handler.request(settingsMessage = "Enable in Settings") { }
        testScope.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Settings guide should be shown")
        }

        // SIMULATE PROCESS DEATH
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: Settings guide restored
        restoredHandler.state.test {
            val restoredState = awaitItem()
            assertTrue(restoredState.showSettingsGuide, "Settings guide should be restored")
        }
    }

    @Test
    fun `Process death User dismisses dialog state is cleared`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Show rationale
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        // User dismisses dialog
        handler.onDismiss()
        testScope.advanceUntilIdle()

        // Verify: State cleared
        handler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale, "Dialog should be dismissed")
        }

        // SIMULATE PROCESS DEATH
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: No dialog shown after restoration
        restoredHandler.state.test {
            val restoredState = awaitItem()
            assertFalse(restoredState.showRationale, "Dialog should NOT be restored")
            assertFalse(restoredState.showSettingsGuide)
        }
    }

    @Test
    fun `Process death User confirms rationale grants permission state cleared`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Show rationale
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        // User confirms and grants permission
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // SIMULATE PROCESS DEATH
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        mockGrantManager.mockStatus = GrantStatus.GRANTED
        testScope.advanceUntilIdle()

        // Verify: No dialog restored (permission granted)
        restoredHandler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale)
            assertFalse(state.showSettingsGuide)
        }
        restoredHandler.status.test {
            assertEquals(GrantStatus.GRANTED, awaitItem())
        }
    }

    // ==================== NoOpSavedStateDelegate Tests ====================

    @Test
    fun `NoOpSavedStateDelegate No state is saved or restored`() = runTest {
        val noOpDelegate = NoOpSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = noOpDelegate
        )

        // Show rationale
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale)
        }

        // NoOp should not save anything
        assertNull(noOpDelegate.restoreState("any_key"), "NoOp should not restore state")

        // Create new handler - state NOT restored
        val newHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = noOpDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: Dialog NOT shown (no restoration)
        newHandler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale, "NoOp should not restore dialog state")
        }
    }

    @Test
    fun `NoOpSavedStateDelegate clear does nothing`() {
        val noOpDelegate = NoOpSavedStateDelegate()

        // Should not throw
        noOpDelegate.saveState("key", "value")
        noOpDelegate.clear("key")
        noOpDelegate.clear("non_existent_key")

        assertNull(noOpDelegate.restoreState("key"))
    }

    // ==================== Multiple Save/Restore Cycles ====================

    @Test
    fun `Multiple dialogs shown and dismissed state tracking works`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // First dialog
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        handler.onDismiss()
        testScope.advanceUntilIdle()

        // Second dialog
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale, "Second dialog should be shown")
        }

        // SIMULATE PROCESS DEATH - Should restore second dialog
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        restoredHandler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale, "Latest dialog state should be restored")
        }
    }

    @Test
    fun `State cleared when user opens Settings`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Show settings guide
        handler.request(settingsMessage = "Enable in Settings") { }
        testScope.advanceUntilIdle()

        // User opens Settings
        handler.onSettingsConfirmed()
        testScope.advanceUntilIdle()

        // Verify: Dialog dismissed
        handler.state.test {
            val state = awaitItem()
            assertFalse(state.showSettingsGuide)
        }

        // SIMULATE PROCESS DEATH while user is in Settings
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: Dialog NOT restored (user is in Settings)
        restoredHandler.state.test {
            val state = awaitItem()
            assertFalse(state.showSettingsGuide, "Dialog should NOT be restored")
        }
    }

    // ==================== Edge Cases ====================

    @Test
    fun `SavedStateDelegate with different keys for different handlers`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        // Two different handlers with same delegate
        val cameraHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        val micHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.MICROPHONE,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // First request for both - denied (no rationale yet)
        cameraHandler.request(rationaleMessage = "Camera needed") { }
        micHandler.request(rationaleMessage = "Microphone needed") { }
        testScope.advanceUntilIdle()

        // Update status to DENIED for second requests
        mockGrantManager.mockStatus = GrantStatus.DENIED

        // Second request for both - rationale shown
        cameraHandler.request(rationaleMessage = "Camera needed") { }
        micHandler.request(rationaleMessage = "Microphone needed") { }
        testScope.advanceUntilIdle()

        // Both dialogs shown
        cameraHandler.state.test {
            assertTrue(awaitItem().showRationale)
        }
        micHandler.state.test {
            assertTrue(awaitItem().showRationale)
        }

        // Verify: Both states saved independently (at least 2 keys: one for each handler)
        assertTrue(savedStateDelegate.getStorageSize() >= 2,
            "Both handlers should save state independently")

        // Dismiss camera
        cameraHandler.onDismiss()
        testScope.advanceUntilIdle()

        // Verify: Camera dialog dismissed, Microphone still shown
        cameraHandler.state.test {
            assertFalse(awaitItem().showRationale, "Camera dialog should be dismissed")
        }
        micHandler.state.test {
            assertTrue(awaitItem().showRationale, "Microphone dialog should still be shown")
        }
    }

    @Test
    fun `SavedStateDelegate persists rationale dialog state`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        val customRationaleMessage = "Custom rationale message for camera"

        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        handler.request(rationaleMessage = customRationaleMessage) { }
        testScope.advanceUntilIdle()

        // SIMULATE PROCESS DEATH
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: Dialog restored (but custom message needs to be passed again)
        restoredHandler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale)
        }
    }

    @Test
    fun `Clear all state after permission granted`() = runTest {
        val savedStateDelegate = FakeSavedStateDelegate()
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Request and grant
        var granted = false
        handler.request(rationaleMessage = "Camera needed") { granted = true }
        testScope.advanceUntilIdle()

        assertTrue(granted)

        // Update status to granted
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        // SIMULATE PROCESS DEATH
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // Verify: No dialogs (permission already granted)
        restoredHandler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale)
            assertFalse(state.showSettingsGuide)
        }
        restoredHandler.status.test {
            assertEquals(GrantStatus.GRANTED, awaitItem())
        }
    }
}
