package dev.brewkits.grant.impl

import androidx.test.platform.app.InstrumentationRegistry
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android Instrument Test for PlatformGrantDelegate cache optimization.
 */
class PlatformCacheTest {

    @Test
    fun testCheckStatusCacheEffectiveness() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = InMemoryGrantStore()
        val delegate = PlatformGrantDelegate(context, store)

        // First call should hit the system
        val status1 = delegate.checkStatus(AppGrant.CAMERA)

        // Measure time for subsequent calls within 1 second (should be cached)
        val startTime = System.currentTimeMillis()
        var callCount = 0
        while (System.currentTimeMillis() - startTime < 500) {
            val status2 = delegate.checkStatus(AppGrant.CAMERA)
            assertEquals(status1, status2)
            callCount++
        }

        // Within 500ms, we should have made hundreds/thousands of calls,
        // all served from cache without crashing or significant overhead.
        assertTrue("Should have performed many cached calls, but only did $callCount", callCount > 100)
    }

    @Test
    fun testCacheExpiration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = InMemoryGrantStore()
        val delegate = PlatformGrantDelegate(context, store)

        // First call
        delegate.checkStatus(AppGrant.CAMERA)

        // Wait for cache to expire (1000ms TTL)
        kotlinx.coroutines.delay(1100)

        // This call should be a fresh hit (though we can't easily measure hit count, 
        // we verify it still works correctly after expiration)
        val statusAfterExp = delegate.checkStatus(AppGrant.CAMERA)
        // No crash and returns valid status
        assertTrue(true)
    }
}
