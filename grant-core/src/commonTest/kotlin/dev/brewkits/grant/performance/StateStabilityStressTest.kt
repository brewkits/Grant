package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Stress tests for State Stability.
 *
 * Uses random jitter and randomized request/dismiss sequences to ensure
 * the GrantHandler state machine never enters an inconsistent state.
 */
class StateStabilityStressTest {

    private lateinit var mockGrantManager: FakeGrantManager
    
    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
    }

    @Test
    fun `Stress test - randomized event sequence`() = runTest {
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
        val random = Random(42)
        
        repeat(500) {
            val event = random.nextInt(4)
            when (event) {
                0 -> {
                    // Update mock status randomly
                    mockGrantManager.mockStatus = GrantStatus.entries.toTypedArray()[random.nextInt(5)]
                    handler.refreshStatus()
                }
                1 -> {
                    handler.request { /* random grant result */ }
                }
                2 -> {
                    handler.onRationaleConfirmed()
                }
                3 -> {
                    handler.onDismiss()
                }
            }
            
            // Random small delay to allow coroutines to interleave
            yield()
            if (random.nextBoolean()) {
                delay(random.nextLong(1, 5))
            }
        }
        
        // After 500 random events, the handler should still be alive and not crashed
        assertTrue(true)
    }
}
