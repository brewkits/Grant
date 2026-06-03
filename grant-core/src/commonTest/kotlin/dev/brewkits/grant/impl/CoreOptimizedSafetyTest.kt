package dev.brewkits.grant.impl

import app.cash.turbine.test
import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Optimized Safety and State Exhaustion tests for the Core logic.
 * 
 * Focuses on robust exception handling, concurrency safety, and state invariants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreOptimizedSafetyTest {

    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    // ====================================================================
    // 1. Exception Safety & Callback Cleanup
    // ====================================================================

    /**
     * Verifies that if the underlying GrantManager throws an exception,
     * the GrantHandler correctly unlocks its Mutex so subsequent requests
     * aren't blocked forever.
     */
    @Test
    fun `mutex should be unlocked after a failure so next request can proceed`() = runTest {
        var shouldFail = true
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> /* Ignore expected test failure */ }
        
        // We use a custom scope with SupervisorJob and ExceptionHandler to isolate the failure
        val supervisorScope = CoroutineScope(this.coroutineContext + SupervisorJob() + exceptionHandler)
        
        val failingManager = object : GrantManager {
            override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
                if (shouldFail) throw IllegalStateException("Simulated OS failure")
                return GrantStatus.NOT_DETERMINED
            }
            override suspend fun request(grant: GrantPermission): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = emptyMap()
            override fun openSettings() {}
            override fun setLauncher(launcher: GrantLauncher) {}
        }
        
        val handler = GrantHandler(failingManager, AppGrant.CAMERA, supervisorScope)
        
        // 1. First request fails inside the coroutine
        handler.request { }
        advanceUntilIdle()
        
        // 2. Second request should succeed if mutex was unlocked in the 'finally' block
        shouldFail = false
        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        advanceUntilIdle()
        
        assertTrue(callbackInvoked, "Mutex should be unlocked even after a previous exception")
        
        supervisorScope.cancel()
    }

    // ====================================================================
    // 2. State Invariant Validation (Turbine)
    // ====================================================================

    @Test
    fun `state should never have both rationale and settings guide visible`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.LOCATION, this)
        
        // Use Turbine to audit state changes
        handler.state.test {
            // Initial state
            val initial = awaitItem()
            assertFalse(initial.showRationale && initial.showSettingsGuide)

            // Simulate a flow that moves through multiple states
            mockGrantManager.mockStatus = GrantStatus.DENIED
            mockGrantManager.mockRequestResult = GrantStatus.DENIED
            
            // First request (denied, usually no rationale shown yet on Android)
            handler.request { }
            advanceUntilIdle()
            
            // Second request (should show rationale)
            mockGrantManager.mockStatus = GrantStatus.DENIED
            handler.request { }
            advanceUntilIdle()

            // Get the current state
            val finalState = handler.state.value
            if (finalState.isVisible) {
                // Logical XOR: only one dialog should be visible at a time
                assertTrue(finalState.showRationale xor finalState.showSettingsGuide, 
                    "Rationale and Settings Guide should never show simultaneously")
            }
            
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ====================================================================
    // 3. Process Death Recovery Verification
    // ====================================================================

    @Test
    fun `should restore UI state after simulated process death`() = runTest {
        val fakeSavedState = object : SavedStateDelegate {
            private val data = mutableMapOf<String, String>()
            override fun saveState(key: String, value: String) { data[key] = value }
            override fun restoreState(key: String): String? = data[key]
            override fun clear(key: String) { data.remove(key) }
        }

        // 1. First instance: show a dialog
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        val handler1 = GrantHandler(mockGrantManager, AppGrant.CAMERA, this, fakeSavedState)
        
        // Trigger settings guide state
        handler1.request(settingsMessage = "GO TO SETTINGS") { }
        advanceUntilIdle()
        
        assertTrue(handler1.state.value.showSettingsGuide)

        // 2. Simulate process death: recreate handler with same saved state storage
        val handler2 = GrantHandler(mockGrantManager, AppGrant.CAMERA, this, fakeSavedState)
        
        // 3. Verify state is restored
        assertTrue(handler2.state.value.isVisible, "Visibility should be restored from SavedState")
        assertTrue(handler2.state.value.showSettingsGuide, "Settings guide state should be restored")
    }

    // ====================================================================
    // 4. Concurrency Safety
    // ====================================================================

    @Test
    fun `mutex should remain usable after dismissal`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        
        // 1. Open a dialog
        mockGrantManager.mockStatus = GrantStatus.DENIED
        
        if (PlatformConfig.isRationaleSupported) {
            // Rationale shown on first request because mockStatus = DENIED
            handler.request { } 
            advanceUntilIdle()
            
            // To simulate a second request, we must first dismiss the dialog
            // BUT wait! If we dismiss it, hasShownRationaleDialog becomes false!
            // Instead, let's confirm the rationale so it proceeds to DENIED again
            mockGrantManager.mockRequestResult = GrantStatus.DENIED
            handler.onRationaleConfirmed()
            advanceUntilIdle()
            
            assertTrue(handler.state.value.showSettingsGuide, "Settings guide should be visible after rationale is confirmed but OS still denies")
        } else {
            // iOS-like logic: Rationale not supported, transitions directly to Settings Guide
            handler.request { }
            advanceUntilIdle()
            assertTrue(handler.state.value.showSettingsGuide, "Settings Guide should be visible after denial on iOS")
        }
        
        // 2. Dismiss it
        handler.onDismiss()
        advanceUntilIdle()
        assertFalse(handler.state.value.isVisible, "Dialog should be hidden after dismissal")
        
        // 3. Request again: should still work (mutex was unlocked)
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        var granted = false
        handler.request { granted = true }
        advanceUntilIdle()
        
        assertTrue(granted, "Handler should still function after a dismissal")
    }
}
