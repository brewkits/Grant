package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Stress tests for Cancellation safety.
 *
 * Verifies that the library handles rapid coroutine cancellation without leaking
 * memory, hanging, or calling into dead continuations.
 */
class CancellationSafetyStressTest {

    private lateinit var mockGrantManager: FakeGrantManager
    
    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
    }

    @Test
    fun `Stress test - rapid cancellation of 1000 requests`() = runTest {
        var activeJobs = 0
        val mutex = Mutex()
        
        val manager = object : GrantManager by mockGrantManager {
            override suspend fun request(grant: GrantPermission): GrantStatus {
                mutex.withLock { activeJobs++ }
                try {
                    delay(5000) // Long request
                    return GrantStatus.GRANTED
                } finally {
                    mutex.withLock { activeJobs-- }
                }
            }
        }

        coroutineScope {
            repeat(1000) {
                val job = launch {
                    val handler = GrantHandler(manager, AppGrant.CAMERA, this)
                    handler.request { /* should not be called if cancelled */ }
                }
                
                // Cancel almost immediately
                delay(1)
                job.cancelAndJoin()
            }
        }

        // Verify that all jobs were actually cancelled and cleaned up
        val finalActiveJobs = mutex.withLock { activeJobs }
        assertTrue(finalActiveJobs == 0, "All jobs should have been cleaned up. Remaining: $finalActiveJobs")
    }

    @Test
    fun `Stress test - cancellation while waiting for OS result`() = runTest {
        var cancellationCalled = false
        
        val manager = object : GrantManager by mockGrantManager {
            override suspend fun request(grant: GrantPermission): GrantStatus {
                return suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation {
                        cancellationCalled = true
                    }
                    // Simulate system dialog that never returns until cancelled
                }
            }
        }

        val job = launch {
            manager.request(AppGrant.CAMERA)
        }
        
        delay(100)
        job.cancelAndJoin()
        
        assertTrue(cancellationCalled, "The delegate's invokeOnCancellation should have been triggered")
    }
}
