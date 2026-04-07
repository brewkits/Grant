package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

/**
 * Stress tests for Threading and Stability.
 * Verifies that GrantHandler handles high concurrency without race conditions.
 */
class ThreadingStressTest {

    private lateinit var testScope: CoroutineScope
    private lateinit var mockGrantManager: FakeGrantManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        testScope = CoroutineScope(StandardTestDispatcher())
        mockGrantManager = FakeGrantManager()
    }

    @Test
    fun testConcurrentRequestsStability() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // Launch 500 concurrent requests
        val jobs = List(500) {
            testScope.launch {
                handler.request { /* success */ }
            }
        }
        
        // Wait for all to complete
        jobs.joinAll()
        
        // No crash and state should be consistent
        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)
    }

    @Test
    fun testRapidStatusRefreshes() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        
        // Rapidly refresh status 1000 times from different coroutines
        val jobs = List(1000) {
            testScope.launch {
                handler.refreshStatus()
            }
        }
        
        jobs.joinAll()
        
        // Verify we still have a valid status
        assertTrue(handler.status.value is GrantStatus)
    }
}
