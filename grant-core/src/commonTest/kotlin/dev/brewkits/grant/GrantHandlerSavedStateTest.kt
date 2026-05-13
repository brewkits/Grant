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
import dev.brewkits.grant.assumeRationaleSupported

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
    fun `GrantHandler with SavedStateDelegate - saves state with custom messages`() = runTest {
        assumeRationaleSupported {
            val handler = GrantHandler(
                grantManager = fakeGrantManager,
                grant = AppGrant.CAMERA,
                scope = testScope,
                savedStateDelegate = savedStateDelegate
            )

            val customRationale = "Custom rationale message"
            val customSettings = "Custom settings message"

            // Request permission (will be denied)
            fakeGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
            handler.request(rationaleMessage = customRationale, settingsMessage = customSettings) { }

            testDispatcher.scheduler.advanceUntilIdle()

            // State should be visible and saved
            val storage = savedStateDelegate.storage
            assertTrue(handler.state.value.isVisible, "UI State should be visible")
            assertEquals("true", storage["grant_handler_is_visible"], "Visible flag should be saved")
            assertEquals("true", storage["grant_handler_show_rationale"], "Rationale flag should be saved")
            assertEquals(customRationale, storage["grant_handler_rationale_msg"], "Rationale message should be saved")
            assertEquals(customSettings, storage["grant_handler_settings_msg"], "Settings message should be saved")
        }
    }

    @Test
    fun `GrantHandler - restores state with custom messages from SavedStateDelegate`() = runTest {
        val customRationale = "Restored rationale"
        val customSettings = "Restored settings"
        
        // Pre-populate saved state
        savedStateDelegate.saveState("grant_handler_is_visible", "true")
        savedStateDelegate.saveState("grant_handler_show_rationale", "true")
        savedStateDelegate.saveState("grant_handler_rationale_msg", customRationale)
        savedStateDelegate.saveState("grant_handler_settings_msg", customSettings)

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
        assertTrue(state.isVisible)
        assertEquals(customRationale, state.rationaleMessage)
        assertEquals(customSettings, state.settingsMessage)
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
