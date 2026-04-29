package dev.brewkits.grant

import dev.brewkits.grant.impl.SimpleGrantManager
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SimpleGrantManagerTest {

    @Test
    fun `test AUTO_GRANT mode`() = runTest {
        val manager = SimpleGrantManager().apply {
            simulationMode = SimpleGrantManager.SimulationMode.AUTO_GRANT
        }
        assertEquals(GrantStatus.GRANTED, manager.request(AppGrant.CAMERA))
        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.CAMERA))
    }

    @Test
    fun `test ALWAYS_DENY mode`() = runTest {
        val manager = SimpleGrantManager().apply {
            simulationMode = SimpleGrantManager.SimulationMode.ALWAYS_DENY
        }
        assertEquals(GrantStatus.DENIED, manager.request(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED, manager.checkStatus(AppGrant.CAMERA))
    }

    @Test
    fun `test REALISTIC mode flow`() = runTest {
        val manager = SimpleGrantManager().apply {
            simulationMode = SimpleGrantManager.SimulationMode.REALISTIC
        }
        // First request: Deny
        assertEquals(GrantStatus.DENIED, manager.request(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED, manager.checkStatus(AppGrant.CAMERA))
        
        // Second request: Deny Always
        assertEquals(GrantStatus.DENIED_ALWAYS, manager.request(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED_ALWAYS, manager.checkStatus(AppGrant.CAMERA))
        
        // Third request: Grant
        assertEquals(GrantStatus.GRANTED, manager.request(AppGrant.CAMERA))
        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.CAMERA))
    }

    @Test
    fun `test reset`() = runTest {
        val manager = SimpleGrantManager().apply {
            simulationMode = SimpleGrantManager.SimulationMode.AUTO_GRANT
        }
        manager.request(AppGrant.CAMERA)
        manager.reset()
        assertEquals(GrantStatus.NOT_DETERMINED, manager.checkStatus(AppGrant.CAMERA))
    }
}
