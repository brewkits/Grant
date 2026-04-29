package dev.brewkits.grant.security

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Security and Integrity tests for the Grant library.
 * 
 * Tests against:
 * - Rationale Loops (UX security)
 * - RawPermission Fuzzing
 * - Status Corruption handling
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityIntegrityTest {

    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    /**
     * SEC-001: Infinite Rationale Loop Protection
     * Ensures that if an OS keeps returning DENIED but shouldShowRationale=true, 
     * we don't loop the user indefinitely.
     */
    @Test
    fun `should prevent infinite rationale loop even if OS returns inconsistent results`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        
        // Setup: OS always returns DENIED
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        
        if (PlatformConfig.isRationaleSupported) {
            // Android-like logic
            handler.request { }
            advanceUntilIdle()
            
            handler.request { }
            advanceUntilIdle()
            assertTrue(handler.state.value.showRationale, "Rationale should be visible on Android after denial")
            
            handler.onRationaleConfirmed()
            advanceUntilIdle()
            assertFalse(handler.state.value.isVisible, "Dialog should hide after denial to prevent loop")
        } else {
            // iOS-like logic: Rationale is NEVER shown
            handler.request { }
            advanceUntilIdle()
            assertFalse(handler.state.value.showRationale, "Rationale should never be visible on iOS")
        }
    }

    /**
     * SEC-002: RawPermission Identifier Sanitization
     * Ensures system handles weird/malformed identifiers gracefully.
     */
    @Test
    fun `should handle malformed RawPermission identifiers`() = runTest {
        val malformedId = "   " // Empty/Blank
        val raw = RawPermission(malformedId, listOf("android.permission.DUMMY"), "Key")
        
        val handler = GrantHandler(mockGrantManager, raw, this)
        handler.refreshStatus()
        advanceUntilIdle()
        
        // Should not crash, just return NOT_DETERMINED or fallback
        assertNotNull(handler.status.value)
    }

    /**
     * SEC-003: Memory Leak Protection - Callback Cleanup
     * Verifies that callbacks are ALWAYS cleared, even on denial.
     */
    @Test
    fun `callback should be cleared on dismissal to prevent memory leaks`() = runTest {
        // This is tricky to check directly on private members, but we can verify 
        // behavior by checking if the callback is NOT invoked later.
        
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        var invoked = false
        
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        handler.request { invoked = true }
        advanceUntilIdle()
        
        // Dismiss
        handler.onDismiss()
        advanceUntilIdle()
        
        // Simulate late grant (e.g. from background)
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()
        
        assertFalse(invoked, "Callback should have been cleared after dismissal")
    }
}
