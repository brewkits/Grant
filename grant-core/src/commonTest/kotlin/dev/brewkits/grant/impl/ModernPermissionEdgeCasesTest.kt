package dev.brewkits.grant.impl

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ModernPermissionEdgeCasesTest {

    @Test
    fun `LOCATION_ALWAYS requires 2-step flow - Step 1 Foreground then request background`() = runTest {
        val mockGrantManager = FakeGrantManager()
        // Start as PARTIAL_GRANTED (foreground OK)
        mockGrantManager.setStatus(AppGrant.LOCATION_ALWAYS, GrantStatus.PARTIAL_GRANTED)
        // Simulate user denying background in OS dialog again
        mockGrantManager.mockRequestResult = GrantStatus.PARTIAL_GRANTED
        
        val handler = GrantHandler(mockGrantManager, AppGrant.LOCATION_ALWAYS, this)
        advanceUntilIdle()

        handler.request { }
        advanceUntilIdle()

        assertTrue(mockGrantManager.requestCalled, "Should try OS request first")
        assertTrue(handler.state.value.showSettingsGuide, "Should trigger settings guide if background request failed")
    }

    @Test
    fun `multiple rapid requests should drop overlapping calls to prevent UI spam`() = runTest {
        val delayingManager = object : GrantManager {
            override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
                delay(1000)
                return GrantStatus.GRANTED
            }
            override suspend fun request(grant: GrantPermission): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = emptyMap()
            override fun openSettings() {}
            override fun setLauncher(launcher: GrantLauncher) {}
        }
        val handler = GrantHandler(delayingManager, AppGrant.CAMERA, this)
        
        var count = 0
        handler.request { count++ }
        runCurrent() 
        handler.request { count++ }
        handler.request { count++ }
        
        advanceUntilIdle()
        
        assertEquals(1, count, "Concurrent requests should be dropped")
    }
}
