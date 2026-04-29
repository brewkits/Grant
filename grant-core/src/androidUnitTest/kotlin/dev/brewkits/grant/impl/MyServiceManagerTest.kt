package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.ServiceStatus
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MyServiceManagerTest {

    private lateinit var delegate: PlatformServiceDelegate
    private lateinit var manager: MyServiceManager

    @BeforeTest
    fun setup() {
        delegate = mockk(relaxed = true)
        manager = MyServiceManager(delegate)
    }

    @Test
    fun `checkServiceStatus delegates to platform delegate`() = runTest {
        coEvery { delegate.checkServiceStatus(ServiceType.LOCATION_GPS) } returns ServiceStatus.ENABLED
        val status = manager.checkServiceStatus(ServiceType.LOCATION_GPS)
        assertEquals(ServiceStatus.ENABLED, status)
        coVerify { delegate.checkServiceStatus(ServiceType.LOCATION_GPS) }
    }

    @Test
    fun `openServiceSettings delegates to platform delegate`() = runTest {
        coEvery { delegate.openServiceSettings(ServiceType.LOCATION_GPS) } returns true
        val result = manager.openServiceSettings(ServiceType.LOCATION_GPS)
        assertTrue(result)
        coVerify { delegate.openServiceSettings(ServiceType.LOCATION_GPS) }
    }
}
