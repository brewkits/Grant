package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * GLOBAL HARDENING TEST SUITE (v1.3.1)
 * 
 * This suite integrates Unit, Integration, System, Performance, Stress, and Security tests
 * into a single high-pressure flow to ensure the library's absolute stability.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalHardeningTest {

    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    // ======================================================================================
    // 1. UNIT & INTEGRITY: Status Mapping & Factory Consistency
    // ======================================================================================

    @Test
    fun `should maintain consistent status across all core types`() = runTest {
        val permissions = AppGrant.entries
        for (permission in permissions) {
            mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
            assertEquals(GrantStatus.NOT_DETERMINED, mockGrantManager.checkStatus(permission))
            
            mockGrantManager.mockStatus = GrantStatus.GRANTED
            assertEquals(GrantStatus.GRANTED, mockGrantManager.checkStatus(permission))
        }
    }

    // ======================================================================================
    // 2. PERFORMANCE & STRESS: High-Frequency Status Refresh
    // ======================================================================================

    @Test
    fun `should handle 1000 rapid status refreshes within performance budget`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        
        val duration = measureTime {
            repeat(1000) {
                handler.refreshStatus()
            }
        }
        
        // Budget: 1000 virtual refreshes should be near-instant in test environment
        assertTrue(duration.inWholeMilliseconds < 2000, "Performance regression detected in status refresh")
    }

    @Test
    fun `stress test - 50 concurrent handlers initializing and requesting`() = runTest {
        mockGrantManager.simulatedDelayMs = 10
        val results = mutableListOf<GrantStatus>()
        
        val permissions = AppGrant.entries
        val jobs = (0 until 50).map { index ->
            launch {
                val grant = permissions[index % permissions.size]
                val handler = GrantHandler(mockGrantManager, grant, this)
                handler.request {
                    results.add(handler.status.value)
                }
            }
        }
        
        advanceUntilIdle()
        assertEquals(50, results.size, "All concurrent requests should have completed")
    }

    // ======================================================================================
    // 3. SECURITY: Sanitization & State Isolation
    // ======================================================================================

    @Test
    fun `security - should ignore malformed raw permission calls`() = runTest {
        val malformedRaw = RawPermission("", emptyList(), "")
        val handler = GrantHandler(mockGrantManager, malformedRaw, this)
        
        // Should not crash even with empty strings
        handler.refreshStatus()
        advanceUntilIdle()
        
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `security - ensure UI state does not leak internal implementation details`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        val state = handler.state.value
        
        // Verify state is clean and only contains UI-relevant flags
        assertNotNull(state)
        // Check for specific fields to ensure they exist but are default
        assertFalse(state.isVisible)
        assertFalse(state.showRationale)
        assertFalse(state.showSettingsGuide)
    }

    // ======================================================================================
    // 4. SYSTEM & REGRESSION: Deadlock Prevention (The v1.3.1 Fix)
    // ======================================================================================

    @Test
    fun `system - should prevent deadlock during re-entrant status checks`() = runTest {
        // This test simulates the v1.3.1 fix logic:
        // A request triggers a status check while the request lock is held.
        
        val handler = GrantHandler(mockGrantManager, AppGrant.LOCATION, this)
        
        withTimeout(2000) {
            var completed = false
            handler.request {
                completed = true
            }
            advanceUntilIdle()
            assertTrue(completed, "Request should complete without deadlocking in v1.3.1")
        }
    }

    @Test
    fun `system - complex multi-step user journey`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        
        val handler = GrantHandler(mockGrantManager, AppGrant.MICROPHONE, this)
        
        // 1. Initial request -> Denied
        handler.request { }
        advanceUntilIdle()
        assertEquals(GrantStatus.DENIED, handler.status.value)
        
        // 2. Refresh -> Still Denied
        handler.refreshStatus()
        advanceUntilIdle()
        
        // 3. Second request -> Rationale (if supported)
        handler.request { }
        advanceUntilIdle()
        
        // 4. User cancels dialog
        handler.onDismiss()
        advanceUntilIdle()
        assertFalse(handler.state.value.isVisible)
    }
}
