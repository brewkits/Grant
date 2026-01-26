package dev.brewkits.grant

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ServiceManager interface default implementations.
 *
 * Note: MyServiceManager and platform-specific error handling are tested
 * in platform-specific test suites (androidTest, iosTest).
 */
class ServiceManagerTest {

    @Test
    fun testIsServiceEnabledReturnsTrueForEnabled() = runTest {
        val manager = TestServiceManager(ServiceStatus.ENABLED)

        val isEnabled = manager.isServiceEnabled(ServiceType.BLUETOOTH)

        assertTrue(isEnabled, "isServiceEnabled should return true when status is ENABLED")
    }

    @Test
    fun testIsServiceEnabledReturnsFalseForDisabled() = runTest {
        val manager = TestServiceManager(ServiceStatus.DISABLED)

        val isEnabled = manager.isServiceEnabled(ServiceType.BLUETOOTH)

        assertFalse(isEnabled, "isServiceEnabled should return false when status is DISABLED")
    }

    @Test
    fun testIsServiceEnabledReturnsFalseForNotAvailable() = runTest {
        val manager = TestServiceManager(ServiceStatus.NOT_AVAILABLE)

        val isEnabled = manager.isServiceEnabled(ServiceType.NFC)

        assertFalse(isEnabled, "isServiceEnabled should return false when status is NOT_AVAILABLE")
    }

    @Test
    fun testIsServiceEnabledReturnsFalseForUnknown() = runTest {
        val manager = TestServiceManager(ServiceStatus.UNKNOWN)

        val isEnabled = manager.isServiceEnabled(ServiceType.WIFI)

        assertFalse(isEnabled, "isServiceEnabled should return false when status is UNKNOWN")
    }

    @Test
    fun testServiceTypeValues() {
        // Verify all service types are accessible
        val types = listOf(
            ServiceType.LOCATION_GPS,
            ServiceType.BLUETOOTH,
            ServiceType.WIFI,
            ServiceType.NFC,
            ServiceType.CAMERA_HARDWARE
        )

        assertTrue(types.isNotEmpty(), "Service types should be defined")
    }
}

/**
 * Fake ServiceManager implementation for testing the interface.
 */
internal class TestServiceManager(
    private val statusToReturn: ServiceStatus
) : ServiceManager {
    override suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        return statusToReturn
    }

    override suspend fun openServiceSettings(service: ServiceType): Boolean {
        return true
    }
}
