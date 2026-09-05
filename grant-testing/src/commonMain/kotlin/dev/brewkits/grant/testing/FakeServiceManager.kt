package dev.brewkits.grant.testing

import dev.brewkits.grant.ServiceManager
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import kotlinx.coroutines.delay

/**
 * An in-memory [ServiceManager] for tests — every [ServiceType] reports the same [mockStatus]
 * (or [mockEnabled], its boolean view) until told otherwise; there is no per-service override
 * because no real consumer of this project has needed one yet. Add [setStatus] the moment one
 * does, following [FakeGrantManager]'s per-permission pattern.
 */
public class FakeServiceManager(
    public var mockStatus: ServiceStatus = ServiceStatus.ENABLED,
) : ServiceManager {

    /** Boolean view of [mockStatus] — setting it maps to [ServiceStatus.ENABLED]/[ServiceStatus.DISABLED]. */
    public var mockEnabled: Boolean
        get() = mockStatus == ServiceStatus.ENABLED
        set(value) {
            mockStatus = if (value) ServiceStatus.ENABLED else ServiceStatus.DISABLED
        }

    /** When non-null, every call throws this instead of returning. */
    public var shouldThrow: Exception? = null

    /** Simulates a slow platform call — every call suspends for this long first. */
    public var simulatedDelayMs: Long = 0

    /** True once [isServiceEnabled] has been called. */
    public var isServiceEnabledCalled: Boolean = false

    /** True once [openServiceSettings] has been called. */
    public var openServiceSettingsCalled: Boolean = false

    /** Every [ServiceType] checked, via either [isServiceEnabled] or [checkServiceStatus]. */
    public val serviceCheckCalls: MutableList<ServiceType> = mutableListOf()

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
