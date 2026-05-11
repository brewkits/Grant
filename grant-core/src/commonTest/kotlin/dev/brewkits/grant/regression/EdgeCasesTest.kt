package dev.brewkits.grant.regression

import dev.brewkits.grant.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class EdgeCasesTest {

    @Test
    fun testGrantHandler_concurrentRequests_callbackAtomicity() = runTest {
        val delayingManager = object : GrantManager {
            override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
                delay(1000)
                return GrantStatus.GRANTED
            }
            override suspend fun request(grant: GrantPermission): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = emptyMap()
            override fun openSettings() {}
        }
        
        val handler = GrantHandler(delayingManager, AppGrant.CAMERA, this)
        
        var callback1Invoked = false
        var callback2Invoked = false

        handler.request { callback1Invoked = true }
        
        // Let the first request start and acquire the lock
        testScheduler.runCurrent()
        
        handler.request { callback2Invoked = true }

        advanceUntilIdle()

        assertTrue(callback1Invoked, "First callback SHOULD be invoked now that atomicity is fixed")
        assertFalse(callback2Invoked, "Second callback should NOT be invoked because it was blocked by the lock and didn't overwrite")
    }
}
