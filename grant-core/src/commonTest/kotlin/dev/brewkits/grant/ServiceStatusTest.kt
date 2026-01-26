package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceStatusTest {

    @Test
    fun testAllServiceStatusesExist() {
        val statuses = ServiceStatus.entries
        assertEquals(4, statuses.size, "Expected 4 service statuses")
    }

    @Test
    fun testIndividualStatuses() {
        val statuses = ServiceStatus.entries

        assertTrue(statuses.contains(ServiceStatus.ENABLED), "ENABLED status should exist")
        assertTrue(statuses.contains(ServiceStatus.DISABLED), "DISABLED status should exist")
        assertTrue(statuses.contains(ServiceStatus.NOT_AVAILABLE), "NOT_AVAILABLE status should exist")
        assertTrue(statuses.contains(ServiceStatus.UNKNOWN), "UNKNOWN status should exist")
    }

    @Test
    fun testStatusEnumNames() {
        assertEquals("ENABLED", ServiceStatus.ENABLED.name)
        assertEquals("DISABLED", ServiceStatus.DISABLED.name)
        assertEquals("NOT_AVAILABLE", ServiceStatus.NOT_AVAILABLE.name)
        assertEquals("UNKNOWN", ServiceStatus.UNKNOWN.name)
    }

    @Test
    fun testStatusEnumOrdinals() {
        assertEquals(0, ServiceStatus.ENABLED.ordinal)
        assertEquals(1, ServiceStatus.DISABLED.ordinal)
        assertEquals(2, ServiceStatus.NOT_AVAILABLE.ordinal)
        assertEquals(3, ServiceStatus.UNKNOWN.ordinal)
    }

    @Test
    fun testStatusValueOf() {
        assertEquals(ServiceStatus.ENABLED, ServiceStatus.valueOf("ENABLED"))
        assertEquals(ServiceStatus.DISABLED, ServiceStatus.valueOf("DISABLED"))
        assertEquals(ServiceStatus.NOT_AVAILABLE, ServiceStatus.valueOf("NOT_AVAILABLE"))
        assertEquals(ServiceStatus.UNKNOWN, ServiceStatus.valueOf("UNKNOWN"))
    }
}
