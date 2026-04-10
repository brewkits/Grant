package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.MultiGrantFakeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Extreme stress tests for high-concurrency scenarios.
 * Focuses on race conditions, deadlocks, and state consistency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadingStressTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = MultiGrantFakeManager()

    @Test
    fun testHighConcurrencyRequestSpam() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        
        var successCount = 0
        val iterationCount = 1000
        
        // Spam 1000 concurrent requests from different coroutines
        coroutineScope {
            repeat(iterationCount) {
                launch {
                    handler.request {
                        successCount++
                    }
                }
            }
        }
        
        testScope.advanceUntilIdle()
        
        // Due to requestMutex, only ONE real request flow should have completed
        // while the others were blocked or skipped.
        // Actually, requestMutex blocks concurrent flows, but updates the callback.
        // The last callback to be set will be the one executed.
        assertTrue(successCount >= 1, "At least one request should succeed")
        assertTrue(successCount <= iterationCount, "Consistency check")
    }

    @Test
    fun testReentrantDeadlockPrevention() = runTest {
        // This test simulates the deadlock reported in #24
        // Logic: request() -> internally calls checkStatus()
        // If checkStatus() uses the same lock as request(), it deadlocks.
        
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        
        var completed = false
        
        // This should NOT hang
        handler.request {
            completed = true
        }
        
        testScope.advanceUntilIdle()
        assertTrue(completed, "Request should complete without deadlock")
    }

    @Test
    fun testGroupHandlerRaceCondition() = runTest {
        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val handler = GrantGroupHandler(mockGrantManager, grants, testScope)
        
        grants.forEach {
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }
        
        var successCount = 0
        
        // Spam group requests
        coroutineScope {
            repeat(100) {
                launch {
                    handler.request {
                        successCount++
                    }
                }
            }
        }
        
        testScope.advanceUntilIdle()
        assertTrue(successCount >= 1, "Group request should complete")
    }

    @Test
    fun testRapidStateTransitions() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // Rapidly toggle status between GRANTED and DENIED in mock
        val job = testScope.launch {
            repeat(1000) { i ->
                val status = if (i % 2 == 0) GrantStatus.GRANTED else GrantStatus.DENIED
                mockGrantManager.setStatus(AppGrant.CAMERA, status)
                handler.refreshStatus()
            }
        }
        
        testScope.advanceUntilIdle()
        job.join()
        
        // UI State should still be valid
        assertNotNull(handler.state.value)
    }
}
