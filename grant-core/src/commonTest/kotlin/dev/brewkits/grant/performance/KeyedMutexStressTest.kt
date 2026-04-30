package dev.brewkits.grant.performance

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Stress test for keyed mutex logic and concurrency safety.
 */
class KeyedMutexStressTest {

    private val mapsMutex = Mutex()
    private val mutexMap = mutableMapOf<String, Mutex>()

    private suspend fun getMutexFor(identifier: String): Mutex =
        mapsMutex.withLock { mutexMap.getOrPut(identifier) { Mutex() } }

    @Test
    fun `Keyed mutex prevents concurrent access to same key but allows different keys`() = runTest {
        val activeOperations = mutableMapOf<String, Int>()
        val opMutex = Mutex()

        val job1 = launch {
            getMutexFor("CAMERA").withLock {
                opMutex.withLock { activeOperations["CAMERA"] = 1 }
                delay(100)
                opMutex.withLock { activeOperations["CAMERA"] = 0 }
            }
        }

        val job2 = launch {
            delay(20)
            getMutexFor("CAMERA").withLock {
                // This should wait for job1
                val active = opMutex.withLock { activeOperations["CAMERA"] ?: 0 }
                assertEquals(0, active, "Keyed mutex failed to block concurrent access to same key")
            }
        }

        val job3 = launch {
            delay(20)
            getMutexFor("LOCATION").withLock {
                // This should NOT wait for job1 or job2
                val activeCamera = opMutex.withLock { activeOperations["CAMERA"] ?: 0 }
                assertEquals(1, activeCamera, "Keyed mutex incorrectly blocked access to different key")
            }
        }

        joinAll(job1, job2, job3)
    }

    @Test
    fun `High concurrency stress test for status cache logic`() = runTest {
        val mockManager = FakeGrantManager()
        mockManager.mockStatus = GrantStatus.NOT_DETERMINED
        
        var callCount = 0
        
        // Simulating the iOS checkStatus logic with mutex
        val statusCacheMap = mutableMapOf<String, GrantStatus>()
        val cacheMutex = Mutex()

        suspend fun checkStatusSimulated(id: String): GrantStatus {
            return getMutexFor(id).withLock {
                val cached = cacheMutex.withLock {
                    statusCacheMap[id]
                }
                if (cached != null) return@withLock cached
                
                // Simulate OS call
                callCount++
                delay(10) 
                val status = GrantStatus.GRANTED
                
                cacheMutex.withLock {
                    statusCacheMap[id] = status
                }
                status
            }
        }

        val iterations = 100
        val coroutines = 10
        
        measureTime {
            coroutineScope {
                repeat(coroutines) {
                    launch {
                        repeat(iterations) {
                            checkStatusSimulated("CAMERA")
                        }
                    }
                }
            }
        }.also { println("Stress test completed in $it") }

        assertEquals(1, callCount, "OS call should have been made only once due to mutex and cache")
    }
}
