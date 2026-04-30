package dev.brewkits.grant.compose

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Optimized Integration tests for Dialog UI Logic.
 * 
 * Verifies that GrantHandler correctly triggers UI state changes
 * which would drive the Compose Dialogs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantDialogIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    @Test
    fun `Verify Rationale dialog state sequence`() = runTest {
        // iOS doesn't support rationale, so we simulate Android/supported platform
        // Standard check: first denial doesn't show rationale (UX optimization)
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // 1. Initial request -> Denied (System)
        handler.request { }
        testScope.advanceUntilIdle()
        assertFalse(handler.state.value.isVisible, "Should NOT show rationale after first system denial")

        // 2. Second request (user clicks again) -> Show Rationale
        mockGrantManager.mockStatus = GrantStatus.DENIED
        handler.request { }
        testScope.advanceUntilIdle()
        
        assertTrue(handler.state.value.isVisible)
        assertTrue(handler.state.value.showRationale)
        assertFalse(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `Verify Settings Guide dialog state sequence`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // Request when permanently denied -> Show Settings Guide
        handler.request { }
        testScope.advanceUntilIdle()
        
        assertTrue(handler.state.value.isVisible)
        assertTrue(handler.state.value.showSettingsGuide)
        assertFalse(handler.state.value.showRationale)
    }

    @Test
    fun `Dismissing dialog should clear UI state`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        handler.request { }
        testScope.advanceUntilIdle()
        assertTrue(handler.state.value.isVisible)

        // Simulate user clicking "Cancel" or dismissing dialog
        handler.onDismiss()
        testScope.advanceUntilIdle()
        
        assertFalse(handler.state.value.isVisible, "State should be reset after dismissal")
    }

    @Test
    fun `Confirming Rationale should hide dialog and trigger request`() = runTest {
        // Setup: In Rationale state
        mockGrantManager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        handler.request { } // Second request to trigger rationale
        testScope.advanceUntilIdle()
        
        assertTrue(handler.state.value.showRationale)

        // Act: User clicks "Continue" on rationale
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Verify
        assertFalse(handler.state.value.isVisible, "Rationale should be hidden immediately after confirmation")
        assertTrue(mockGrantManager.requestCalled, "System request should be triggered after rationale confirmation")
    }
}
