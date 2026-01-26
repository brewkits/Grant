package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceTypeTest {

    @Test
    fun testAllServiceTypesExist() {
        val services = ServiceType.entries
        assertEquals(5, services.size, "Expected 5 service types")
    }

    @Test
    fun testIndividualServiceTypes() {
        val services = ServiceType.entries

        assertTrue(services.contains(ServiceType.LOCATION_GPS), "LOCATION_GPS service should exist")
        assertTrue(services.contains(ServiceType.BLUETOOTH), "BLUETOOTH service should exist")
        assertTrue(services.contains(ServiceType.WIFI), "WIFI service should exist")
        assertTrue(services.contains(ServiceType.NFC), "NFC service should exist")
        assertTrue(services.contains(ServiceType.CAMERA_HARDWARE), "CAMERA_HARDWARE service should exist")
    }

    @Test
    fun testServiceEnumNames() {
        assertEquals("LOCATION_GPS", ServiceType.LOCATION_GPS.name)
        assertEquals("BLUETOOTH", ServiceType.BLUETOOTH.name)
        assertEquals("WIFI", ServiceType.WIFI.name)
        assertEquals("NFC", ServiceType.NFC.name)
        assertEquals("CAMERA_HARDWARE", ServiceType.CAMERA_HARDWARE.name)
    }

    @Test
    fun testServiceEnumOrdinals() {
        assertEquals(0, ServiceType.LOCATION_GPS.ordinal)
        assertEquals(1, ServiceType.BLUETOOTH.ordinal)
        assertEquals(2, ServiceType.WIFI.ordinal)
        assertEquals(3, ServiceType.NFC.ordinal)
        assertEquals(4, ServiceType.CAMERA_HARDWARE.ordinal)
    }

    @Test
    fun testServiceValueOf() {
        assertEquals(ServiceType.LOCATION_GPS, ServiceType.valueOf("LOCATION_GPS"))
        assertEquals(ServiceType.BLUETOOTH, ServiceType.valueOf("BLUETOOTH"))
        assertEquals(ServiceType.WIFI, ServiceType.valueOf("WIFI"))
        assertEquals(ServiceType.NFC, ServiceType.valueOf("NFC"))
        assertEquals(ServiceType.CAMERA_HARDWARE, ServiceType.valueOf("CAMERA_HARDWARE"))
    }
}
