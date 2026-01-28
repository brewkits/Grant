package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for GrantHandler with SavedStateDelegate integration.
 */
class GrantHandlerSavedStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeGrantManager: FakeGrantManager
    private lateinit var testScope: CoroutineScope
    private lateinit var savedStateDelegate: TestSavedStateDelegate

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeGrantManager = FakeGrantManager()
        testScope = CoroutineScope(testDispatcher)
        savedStateDelegate = TestSavedStateDelegate()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GrantHandler without SavedStateDelegate - no state persistence`() = runTest {
        val handler = GrantHandler(
            grantManager = fakeGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
            // No savedStateDelegate - uses NoOpSavedStateDelegate
        )

        // Request permission (will be denied)
        fakeGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        handler.request { }

        testDispatcher.scheduler.advanceUntilIdle()

        // State should be visible
        assertTrue(handler.state.value.isVisible)

        // Check that nothing was saved
        assertTrue(savedStateDelegate.storage.isEmpty())
    }

    @Test
    fun `GrantHandler with SavedStateDelegate - saves state`() = runTest {
        val handler = GrantHandler(
            grantManager = fakeGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Request permission (will be denied)
        fakeGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        handler.request { }

        testDispatcher.scheduler.advanceUntilIdle()

        // State should be visible and saved
        assertTrue(handler.state.value.isVisible)
        assertEquals("true", savedStateDelegate.storage["grant_handler_is_visible"])
        assertEquals("true", savedStateDelegate.storage["grant_handler_show_rationale"])
        assertEquals("false", savedStateDelegate.storage["grant_handler_show_settings"])
    }

    @Test
    fun `GrantHandler - restores state from SavedStateDelegate on init`() = runTest {
        // Pre-populate saved state (simulating process death recovery)
        savedStateDelegate.saveState("grant_handler_is_visible", "true")
        savedStateDelegate.saveState("grant_handler_show_rationale", "true")
        savedStateDelegate.saveState("grant_handler_show_settings", "false")

        // Create handler - should restore state
        val handler = GrantHandler(
            grantManager = fakeGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // State should be restored
        val state = handler.state.value
        assertTrue(state.isVisible, "Dialog should be visible after restoration")
        assertTrue(state.showRationale, "Rationale should be shown after restoration")
        assertFalse(state.showSettingsGuide, "Settings guide should not be shown")
    }

    @Test
    fun `GrantHandler - dismissing dialog clears saved state`() = runTest {
        val handler = GrantHandler(
            grantManager = fakeGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Request permission (will be denied)
        fakeGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        handler.request { }
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify state is saved
        assertEquals("true", savedStateDelegate.storage["grant_handler_is_visible"])

        // Dismiss dialog
        handler.onDismiss()
        testDispatcher.scheduler.advanceUntilIdle()

        // State should be cleared
        assertFalse(handler.state.value.isVisible)
        assertEquals("false", savedStateDelegate.storage["grant_handler_is_visible"])
    }

    @Test
    fun `GrantHandler - granting permission clears saved state`() = runTest {
        val handler = GrantHandler(
            grantManager = fakeGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Initially denied
        fakeGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        handler.request { }
        testDispatcher.scheduler.advanceUntilIdle()

        // Confirm rationale
        fakeGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        handler.onRationaleConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        // State should be cleared
        assertFalse(handler.state.value.isVisible)
        assertEquals("false", savedStateDelegate.storage["grant_handler_is_visible"])
    }

    /**
     * Test implementation of SavedStateDelegate for testing.
     */
    private class TestSavedStateDelegate : SavedStateDelegate {
        val storage = mutableMapOf<String, String>()

        override fun saveState(key: String, value: String) {
            storage[key] = value
        }

        override fun restoreState(key: String): String? {
            return storage[key]
        }

        override fun clear(key: String) {
            storage.remove(key)
        }
    }
}
