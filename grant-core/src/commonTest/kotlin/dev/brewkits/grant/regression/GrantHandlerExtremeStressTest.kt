package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Extreme stress tests to ensure zero regressions for Issue #33 and related race conditions.
 */
class GrantHandlerExtremeStressTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `STRESS - Rapid-fire concurrent requests should never leak state`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            simulatedDelayMs = 50 // Simulate some network/OS delay
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        val results = mutableListOf<Int>()
        
        // Launch 100 concurrent requests
        val jobs = List(100) {
            async {
                handler.request { results.add(1) }
            }
        }
        
        jobs.awaitAll()
        advanceUntilIdle()

        // Only one should have successfully acquired the mutex and fired the callback
        // The others should be silently ignored by tryLock()
        assertEquals(1, results.size, "Concurrent requests MUST be serialized and only one should execute")
        assertFalse(handler.state.value.isVisible, "UI state must be clean after stress")
    }

    @Test
    fun `EDGE - Sequential upgrade from partial to full location`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            // Start with partial
            mockStatus = GrantStatus.PARTIAL_GRANTED
            // First request results in GRANTED (user allowed Always in OS dialog)
            mockRequestResult = GrantStatus.GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        var granted = 0
        handler.request { granted++ }
        
        // Before advancing, it should have triggered a request check
        advanceUntilIdle()

        assertTrue(manager.requestCalled, "Must have called request() to attempt upgrade from PARTIAL")
        assertEquals(1, granted, "Should be granted after successful upgrade")
        assertFalse(handler.state.value.showSettingsGuide, "Should not show settings if OS dialog succeeded")
    }

    @Test
    fun `EDGE - Sequential upgrade but user denies background`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.PARTIAL_GRANTED
            // User denies in OS dialog again -> stays PARTIAL
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        handler.request { }
        advanceUntilIdle()

        assertTrue(manager.requestCalled, "Should have attempted OS request")
        assertTrue(handler.state.value.showSettingsGuide, "Should show settings guide after OS denial of background upgrade")
    }

    @Test
    fun `STRESS - Repeatedly hitting request while in PARTIAL_GRANTED`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.PARTIAL_GRANTED
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        // User taps 10 times quickly
        repeat(10) {
            handler.request { }
            // Move time just enough to let one cycle attempt to start
            delay(10) 
        }
        
        advanceUntilIdle()

        // Only the first one that got the lock should have proceeded. 
        // Subsequent ones while lock is held are ignored.
        // Once the lock is released, a new tap WOULD trigger another requestInternal call.
        assertTrue(manager.requestCalled, "At least one request should have been fired")
        assertTrue(handler.state.value.showSettingsGuide, "Should end up in settings guide")
    }
}
