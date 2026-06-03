package dev.brewkits.grant.property

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertTrue

class GrantPropertyTest {

    @Test
    fun testGrantHandlerTermination() = runTest {
        val mockGrantManager = FakeGrantManager()
        GrantStatus.values().forEach { status ->
            mockGrantManager.mockStatus = status
            mockGrantManager.mockRequestResult = status
            val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)
            
            // Should finish flow and invoke callback if applicable
            var callbackInvoked = false
            handler.request { callbackInvoked = true }
            
            advanceUntilIdle()
            
            // Just assert it doesn't crash and status is updated correctly.
            // onGranted might not be invoked for DENIED statuses.
            kotlin.test.assertEquals(status, handler.status.value, "Handler status should match the mocked status")
        }
    }
}
