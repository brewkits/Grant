package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Advanced Stress Tests for the Grant library.
 * Focuses on high concurrency, memory stability, and performance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrencyStressTest {

    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    /**
     * STRESS-001: High Concurrency Spam
     * Spams 100 concurrent requests to a single GrantHandler.
     * Logic: Only 1 should hold the lock, others should be dropped/queued safely.
     */
    @Test
    fun `should handle 100 concurrent requests without crashing or deadlock`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        mockGrantManager.simulatedDelayMs = 50 // Simulate some OS processing time
        
        var successCount = 0
        val jobs = List(100) {
            launch {
                handler.request {
                    successCount++
                }
            }
        }
        
        // Wait for all to finish
        advanceTimeBy(1000)
        advanceUntilIdle()
        
        // Based on Mutex.tryLock() implementation:
        // Only the FIRST request gets the lock. The other 99 return immediately.
        assertEquals(1, successCount, "Only one request should have proceeded while locked")
    }

    /**
     * STRESS-002: Rapid Sequential Requests
     * Tests performance of 500 sequential status checks.
     */
    @Test
    fun `sequential performance of 500 status checks`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.LOCATION, this)
        
        val startTime = currentTime
        repeat(500) {
            handler.refreshStatus()
        }
        advanceUntilIdle()
        val duration = currentTime - startTime
        
        // Ensure it completes in a reasonable time (Virtual time)
        assertTrue(duration < 1000, "500 checks should be highly efficient")
    }

    /**
     * STRESS-003: Cancellation Safety under Load
     * Launch request and immediately cancel scope. Ensure no leaking coroutines.
     */
    @Test
    fun `ensure cancellation during active request releases resources`() = runTest {
        val customScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val handler = GrantHandler(mockGrantManager, AppGrant.MICROPHONE, customScope)
        
        mockGrantManager.simulatedDelayMs = 5000
        handler.request { }
        
        // Start processing
        testScheduler.advanceTimeBy(1000)
        
        // Act: Cancel the entire scope (simulating Activity destruction or ViewModel cleared)
        customScope.cancel()
        
        testScheduler.advanceUntilIdle()
        
        // Verify we can start a new request on a NEW scope (Mutex should be released)
        val newHandler = GrantHandler(mockGrantManager, AppGrant.MICROPHONE, this)
        mockGrantManager.simulatedDelayMs = 0
        var success = false
        newHandler.request { success = true }
        advanceUntilIdle()
        
        assertTrue(success, "Mutex should be released even if previous scope was cancelled")
    }
}
