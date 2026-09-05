package dev.brewkits.grant.testing

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeServiceManagerTest {

    @Test
    fun `defaults to ENABLED`() = runTest {
        val manager = FakeServiceManager()

        assertEquals(ServiceStatus.ENABLED, manager.checkServiceStatus(ServiceType.LOCATION_GPS))
        assertTrue(manager.isServiceEnabled(ServiceType.LOCATION_GPS))
    }

    @Test
    fun `mockEnabled is a boolean view of mockStatus`() = runTest {
        val manager = FakeServiceManager()
        manager.mockEnabled = false

        assertEquals(ServiceStatus.DISABLED, manager.mockStatus)
        assertFalse(manager.isServiceEnabled(ServiceType.BLUETOOTH))
    }

    @Test
    fun `constructor accepts a starting status`() = runTest {
        val manager = FakeServiceManager(mockStatus = ServiceStatus.DISABLED)

        assertEquals(ServiceStatus.DISABLED, manager.checkServiceStatus(ServiceType.WIFI))
    }

    @Test
    fun `calls are tracked`() = runTest {
        val manager = FakeServiceManager()
        manager.isServiceEnabled(ServiceType.LOCATION_GPS)
        manager.openServiceSettings(ServiceType.BLUETOOTH)

        assertTrue(manager.isServiceEnabledCalled)
        assertTrue(manager.openServiceSettingsCalled)
        assertEquals(listOf(ServiceType.LOCATION_GPS), manager.serviceCheckCalls)
    }

    @Test
    fun `shouldThrow makes every call throw`() = runTest {
        val manager = FakeServiceManager()
        manager.shouldThrow = IllegalStateException("simulated")

        assertFailsWith<IllegalStateException> { manager.checkServiceStatus(ServiceType.WIFI) }
    }
}
