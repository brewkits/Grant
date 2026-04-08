package dev.brewkits.grant.impl

import androidx.test.platform.app.InstrumentationRegistry
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.*
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertTrue

class ThreadingStabilityTest {

    @Test
    fun testConcurrentCheckStatusDoesNotCrash() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
        
        val dispatcher = Executors.newFixedThreadPool(10).asCoroutineDispatcher()
        
        // Stress test: 100 concurrent status checks
        val jobs = List(100) {
            launch(dispatcher) {
                delegate.checkStatus(AppGrant.CAMERA)
            }
        }
        jobs.joinAll()
        assertTrue(true, "Concurrent checks finished without crashing")
    }
}
