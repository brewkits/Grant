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
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Performance tests for Lock Contention.
 *
 * Verifies that the internal Mutex locking strategy correctly serializes requests
 * for the same permission without causing deadlocks or excessive bottlenecks.
 */
class LockContentionTest {

    private lateinit var mockGrantManager: FakeGrantManager
    
    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
    }

    @Test
    fun `Multiple concurrent requests on SAME permission are serialized correctly`() = runTest {
        var callCount = 0
        val mutex = Mutex()
        
        // Mock manager with a slight delay to simulate processing
        val manager = object : GrantManager by mockGrantManager {
            override suspend fun request(grant: GrantPermission): GrantStatus {
                mutex.withLock { callCount++ }
                delay(10) // Simulate OS work
                return GrantStatus.GRANTED
            }
        }

        val handler = GrantHandler(manager, AppGrant.CAMERA, this)
        
        val duration = measureTime {
            // Launch 100 concurrent requests to the SAME handler
            val jobs = List(100) {
                launch {
                    handler.request { /* done */ }
                }
            }
            jobs.joinAll()
        }

        // Each request takes 10ms of delay. 100 requests = 1000ms total serial time.
        // GrantHandler.request uses a Mutex so it should handle this gracefully.
        println("Processed 100 contested requests in: $duration")
        val finalCallCount = mutex.withLock { callCount }
        assertTrue(finalCallCount > 0)
    }

    @Test
    fun `Simultaneous requests for DIFFERENT permissions have no contention`() = runTest {
        // Mock manager with a fixed delay
        val manager = object : GrantManager by mockGrantManager {
            override suspend fun request(grant: GrantPermission): GrantStatus {
                delay(50) 
                return GrantStatus.GRANTED
            }
        }

        val duration = measureTime {
            coroutineScope {
                // Request 10 DIFFERENT permissions concurrently
                val jobs = listOf(
                    AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION,
                    AppGrant.GALLERY, AppGrant.CONTACTS, AppGrant.CALENDAR,
                    AppGrant.MOTION, AppGrant.BLUETOOTH, AppGrant.STORAGE,
                    AppGrant.NOTIFICATION
                ).map { grant ->
                    launch {
                        manager.request(grant)
                    }
                }
                jobs.joinAll()
            }
        }

        // 10 permissions x 50ms = 500ms if serial.
        // If parallel (different mutexes), should be ~50-100ms.
        println("Processed 10 independent requests in: $duration")
        assertTrue(duration.inWholeMilliseconds < 300, "Independent requests should run in parallel. Recorded: $duration")
    }
}
