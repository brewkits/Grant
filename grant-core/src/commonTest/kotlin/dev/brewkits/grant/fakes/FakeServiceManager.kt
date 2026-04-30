package dev.brewkits.grant.fakes

import dev.brewkits.grant.ServiceManager
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import kotlinx.coroutines.delay

/**
 * Fake ServiceManager for testing.
 */
internal class FakeServiceManager : ServiceManager {

    var mockStatus: ServiceStatus = ServiceStatus.ENABLED
    var mockEnabled: Boolean 
        get() = mockStatus == ServiceStatus.ENABLED
        set(value) {
            mockStatus = if (value) ServiceStatus.ENABLED else ServiceStatus.DISABLED
        }

    var shouldThrow: Exception? = null
    var simulatedDelayMs: Long = 0

    var isServiceEnabledCalled = false
    var openServiceSettingsCalled = false
    val serviceCheckCalls = mutableListOf<ServiceType>()

    private suspend fun simulateWork() {
        shouldThrow?.let { throw it }
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
    }

    override suspend fun isServiceEnabled(service: ServiceType): Boolean {
        simulateWork()
        isServiceEnabledCalled = true
        serviceCheckCalls.add(service)
        return mockStatus == ServiceStatus.ENABLED
    }

    override suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        simulateWork()
        serviceCheckCalls.add(service)
        return mockStatus
    }

    override suspend fun openServiceSettings(service: ServiceType): Boolean {
        simulateWork()
        openServiceSettingsCalled = true
        return true
    }
}
