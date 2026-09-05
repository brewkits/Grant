package dev.brewkits.grant

import dev.brewkits.grant.testing.FakeGrantManager
import dev.brewkits.grant.testing.FakeServiceManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GrantAndServiceCheckerTest {

    // --- Location Ready Tests ---

    @Test
    fun testLocationReady_bothGrantedAndEnabled() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.ENABLED)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.Ready>(status)
    }

    @Test
    fun testLocationReady_withLocationAlways() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.ENABLED)

        val status = checker.checkLocationReady(AppGrant.LOCATION_ALWAYS)

        assertIs<LocationReadyStatus.Ready>(status)
    }

    @Test
    fun testLocationReady_grantDeniedServiceEnabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED, ServiceStatus.ENABLED)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.GrantDenied>(status)
        assertEquals(GrantStatus.DENIED, (status as LocationReadyStatus.GrantDenied).grantStatus)
    }

    @Test
    fun testLocationReady_grantDeniedAlwaysServiceEnabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED_ALWAYS, ServiceStatus.ENABLED)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.GrantDenied>(status)
        assertEquals(GrantStatus.DENIED_ALWAYS, (status as LocationReadyStatus.GrantDenied).grantStatus)
    }

    @Test
    fun testLocationReady_grantGrantedServiceDisabled() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.DISABLED)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.ServiceDisabled>(status)
    }

    @Test
    fun testLocationReady_grantGrantedServiceNotAvailable() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.NOT_AVAILABLE)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.ServiceDisabled>(status)
    }

    @Test
    fun testLocationReady_bothDeniedAndDisabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED, ServiceStatus.DISABLED)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.BothRequired>(status)
        assertEquals(GrantStatus.DENIED, (status as LocationReadyStatus.BothRequired).grantStatus)
    }

    @Test
    fun testLocationReady_notDeterminedServiceDisabled() = runTest {
        val checker = createChecker(GrantStatus.NOT_DETERMINED, ServiceStatus.DISABLED)

        val status = checker.checkLocationReady()

        assertIs<LocationReadyStatus.BothRequired>(status)
        assertEquals(GrantStatus.NOT_DETERMINED, (status as LocationReadyStatus.BothRequired).grantStatus)
    }

    // --- Bluetooth Ready Tests ---

    @Test
    fun testBluetoothReady_bothGrantedAndEnabled() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.ENABLED)

        val status = checker.checkBluetoothReady()

        assertIs<BluetoothReadyStatus.Ready>(status)
    }

    @Test
    fun testBluetoothReady_grantDeniedServiceEnabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED, ServiceStatus.ENABLED)

        val status = checker.checkBluetoothReady()

        assertIs<BluetoothReadyStatus.GrantDenied>(status)
        assertEquals(GrantStatus.DENIED, (status as BluetoothReadyStatus.GrantDenied).grantStatus)
    }

    @Test
    fun testBluetoothReady_grantGrantedServiceDisabled() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.DISABLED)

        val status = checker.checkBluetoothReady()

        assertIs<BluetoothReadyStatus.ServiceDisabled>(status)
    }

    @Test
    fun testBluetoothReady_bothDeniedAndDisabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED_ALWAYS, ServiceStatus.DISABLED)

        val status = checker.checkBluetoothReady()

        assertIs<BluetoothReadyStatus.BothRequired>(status)
        assertEquals(GrantStatus.DENIED_ALWAYS, (status as BluetoothReadyStatus.BothRequired).grantStatus)
    }

    // --- Generic CheckReady Tests ---

    @Test
    fun testCheckReady_bothGrantedAndEnabled() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.ENABLED)

        val status = checker.checkReady(AppGrant.CAMERA, ServiceType.CAMERA_HARDWARE)

        assertEquals(GrantStatus.GRANTED, status.grantStatus)
        assertEquals(ServiceStatus.ENABLED, status.serviceStatus)
        assertTrue(status.isReady)
        assertEquals("Ready to use", status.message)
    }

    @Test
    fun testCheckReady_grantDeniedServiceEnabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED, ServiceStatus.ENABLED)

        val status = checker.checkReady(AppGrant.CAMERA, ServiceType.CAMERA_HARDWARE)

        assertEquals(GrantStatus.DENIED, status.grantStatus)
        assertEquals(ServiceStatus.ENABLED, status.serviceStatus)
        assertFalse(status.isReady)
        assertTrue(status.message.contains("Grant required"))
        assertTrue(status.message.contains("DENIED"))
    }

    @Test
    fun testCheckReady_grantGrantedServiceDisabled() = runTest {
        val checker = createChecker(GrantStatus.GRANTED, ServiceStatus.DISABLED)

        val status = checker.checkReady(AppGrant.BLUETOOTH, ServiceType.BLUETOOTH)

        assertEquals(GrantStatus.GRANTED, status.grantStatus)
        assertEquals(ServiceStatus.DISABLED, status.serviceStatus)
        assertFalse(status.isReady)
        assertTrue(status.message.contains("Service required"))
        assertTrue(status.message.contains("DISABLED"))
    }

    @Test
    fun testCheckReady_bothDeniedAndDisabled() = runTest {
        val checker = createChecker(GrantStatus.DENIED, ServiceStatus.DISABLED)

        val status = checker.checkReady(AppGrant.LOCATION, ServiceType.LOCATION_GPS)

        assertEquals(GrantStatus.DENIED, status.grantStatus)
        assertEquals(ServiceStatus.DISABLED, status.serviceStatus)
        assertFalse(status.isReady)
        assertTrue(status.message.contains("Grant and service both required"))
    }

    @Test
    fun testCheckReady_notDeterminedServiceUnknown() = runTest {
        val checker = createChecker(GrantStatus.NOT_DETERMINED, ServiceStatus.UNKNOWN)

        val status = checker.checkReady(AppGrant.MICROPHONE, ServiceType.BLUETOOTH)

        assertEquals(GrantStatus.NOT_DETERMINED, status.grantStatus)
        assertEquals(ServiceStatus.UNKNOWN, status.serviceStatus)
        assertFalse(status.isReady)
        assertTrue(status.message.contains("Grant and service both required"))
    }

    @Test
    fun testCheckReady_grantDeniedAlwaysServiceNotAvailable() = runTest {
        val checker = createChecker(GrantStatus.DENIED_ALWAYS, ServiceStatus.NOT_AVAILABLE)

        val status = checker.checkReady(AppGrant.CONTACTS, ServiceType.NFC)

        assertEquals(GrantStatus.DENIED_ALWAYS, status.grantStatus)
        assertEquals(ServiceStatus.NOT_AVAILABLE, status.serviceStatus)
        assertFalse(status.isReady)
        assertTrue(status.message.contains("Grant and service both required"))
    }

    // --- ReadyStatus Message Tests ---

    @Test
    fun testReadyStatusMessage_ready() {
        val status = ReadyStatus(
            grantStatus = GrantStatus.GRANTED,
            serviceStatus = ServiceStatus.ENABLED,
            isReady = true
        )

        assertEquals("Ready to use", status.message)
    }

    @Test
    fun testReadyStatusMessage_grantRequired() {
        val status = ReadyStatus(
            grantStatus = GrantStatus.DENIED,
            serviceStatus = ServiceStatus.ENABLED,
            isReady = false
        )

        assertTrue(status.message.contains("Grant required"))
        assertTrue(status.message.contains("DENIED"))
    }

    @Test
    fun testReadyStatusMessage_serviceRequired() {
        val status = ReadyStatus(
            grantStatus = GrantStatus.GRANTED,
            serviceStatus = ServiceStatus.DISABLED,
            isReady = false
        )

        assertTrue(status.message.contains("Service required"))
        assertTrue(status.message.contains("DISABLED"))
    }

    @Test
    fun testReadyStatusMessage_bothRequired() {
        val status = ReadyStatus(
            grantStatus = GrantStatus.NOT_DETERMINED,
            serviceStatus = ServiceStatus.DISABLED,
            isReady = false
        )

        assertEquals("Grant and service both required", status.message)
    }

    // --- Helper Methods ---

    private fun createChecker(
        grantStatus: GrantStatus,
        serviceStatus: ServiceStatus
    ): GrantAndServiceChecker {
        // mockRequestResult = grantStatus (not the FakeGrantManager default of GRANTED):
        // this checker's own tests expect request() to echo the same status checkStatus()
        // reports, matching the single-value fake this replaced.
        val grantManager = FakeGrantManager(mockStatus = grantStatus, mockRequestResult = grantStatus)
        val serviceManager = FakeServiceManager(mockStatus = serviceStatus)
        return GrantAndServiceChecker(grantManager, serviceManager)
    }
}
