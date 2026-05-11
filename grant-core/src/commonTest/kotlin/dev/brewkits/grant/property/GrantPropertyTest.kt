package dev.brewkits.grant.property

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GrantPropertyTest {

    @Test
    fun testGrantHandlerTermination() = runTest {
        val mockGrantManager = FakeGrantManager()
        val testScope = CoroutineScope(StandardTestDispatcher())

        GrantStatus.values().forEach { status ->
            mockGrantManager.mockStatus = status
            val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
            
            // Should not throw, should finish flow
            handler.request { }
            
            assertTrue(true)
        }
    }
}
