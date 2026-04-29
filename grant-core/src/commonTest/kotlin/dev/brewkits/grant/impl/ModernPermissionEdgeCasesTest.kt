package dev.brewkits.grant.impl

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Modern Edge Case tests for the Grant library (Context: 2026).
 *
 * Covers:
 * - Android 14+ / iOS 14+ Partial Granted (Gallery/Photos)
 * - Android 12+ / iOS 14+ Approximate Location
 * - Background Location Upgrade logic
 * - One-time permissions (simulated)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModernPermissionEdgeCasesTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    // ====================================================================
    // 1. Partial Access (Android 14+ / iOS 14+)
    // ====================================================================

    @Test
    fun `GALLERY status should support PARTIAL_GRANTED`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.GALLERY, testScope)
        
        // Simulating user selecting "Select photos and videos" (Android 14) or "Limited" (iOS)
        mockGrantManager.setStatus(AppGrant.GALLERY, GrantStatus.PARTIAL_GRANTED)
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    @Test
    fun `request GALLERY when already PARTIAL_GRANTED should still invoke callback`() = runTest {
        mockGrantManager.setStatus(AppGrant.GALLERY, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(mockGrantManager, AppGrant.GALLERY, testScope)
        
        var callbackInvoked = false
        handler.request { callbackInvoked = true }
        testScope.advanceUntilIdle()

        // By design, PARTIAL_GRANTED should be treated as "good enough" to proceed,
        // but the UI might want to show a badge or message.
        assertTrue(callbackInvoked, "Callback should be invoked even with partial access")
    }

    // ====================================================================
    // 2. Location Precision (Android 12+ / iOS 14+)
    // ====================================================================

    @Test
    fun `LOCATION status should reflect approximate access`() = runTest {
        // In this library, approximate location (Coarse on Android) is mapped to PARTIAL_GRANTED
        // or a specific state if the implementation supports it. 
        // Based on GrantStatus.kt, we use PARTIAL_GRANTED for "partial" states.
        
        val handler = GrantHandler(mockGrantManager, AppGrant.LOCATION, testScope)
        
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.PARTIAL_GRANTED)
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)
    }

    // ====================================================================
    // 3. Background Location Upgrade (Android 10+)
    // ====================================================================

    @Test
    fun `LOCATION_ALWAYS requires 2-step flow - Step 1 Foreground`() = runTest {
        // On Android 10+, you can't request background location directly if foreground isn't granted.
        // The implementation in PlatformGrantDelegate.android.kt handles this.
        
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        // Simulate foreground granted, but background still needs to be requested
        mockGrantManager.setRequestResult(AppGrant.LOCATION_ALWAYS, GrantStatus.PARTIAL_GRANTED)

        val handler = GrantHandler(mockGrantManager, AppGrant.LOCATION_ALWAYS, testScope)
        
        handler.request { }
        testScope.advanceUntilIdle()

        // Verify request was called
        assertTrue(mockGrantManager.requestCalled)
    }

    // ====================================================================
    // 4. One-time Permissions (Android 11+)
    // ====================================================================

    @Test
    fun `status should revert to NOT_DETERMINED if one-time permission expires`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // 1. Granted "Only this time"
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        handler.refreshStatus()
        testScope.advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)

        // 2. System revokes it (e.g. app process died or timeout)
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        handler.refreshStatus()
        testScope.advanceUntilIdle()
        
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    // ====================================================================
    // 5. Permanent Denial (Don't ask again)
    // ====================================================================

    @Test
    fun `should transition to DENIED_ALWAYS when user checks Don't ask again`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        val handler = GrantHandler(mockGrantManager, AppGrant.MICROPHONE, testScope)
        
        // This simulates a request where the user clicks "Deny & don't ask again"
        handler.request { }
        testScope.advanceUntilIdle()

        // Update status to match request result
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
        
        // Next request should show settings guide
        handler.request { }
        testScope.advanceUntilIdle()
        
        assertTrue(handler.state.value.showSettingsGuide)
    }

    // ====================================================================
    // 6. Rapid Concurrent Requests (Debouncing)
    // ====================================================================

    @Test
    fun `multiple rapid requests should drop overlapping calls to prevent UI spam`() = runTest {
        // Setup: A GrantManager that stays in NOT_DETERMINED to hold the lock
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        var count = 0
        repeat(5) {
            handler.request { count++ }
        }
        
        // Simulate grant after some time
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        testScope.advanceUntilIdle()
        
        // Only the FIRST request should have succeeded in acquiring the lock.
        // The other 4 should have returned immediately due to tryLock() failing.
        assertEquals(1, count, "Concurrent requests should be dropped to prevent redundant dialogs/logic")
    }
}
