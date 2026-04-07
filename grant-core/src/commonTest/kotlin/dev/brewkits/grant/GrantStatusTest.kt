package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantStatusTest {

    @Test
    fun testAllGrantStatusesExist() {
        val statuses = GrantStatus.entries
        assertEquals(5, statuses.size, "Expected 5 grant statuses")
    }

    @Test
    fun testIndividualStatuses() {
        val statuses = GrantStatus.entries

        assertTrue(statuses.contains(GrantStatus.GRANTED), "GRANTED status should exist")
        assertTrue(statuses.contains(GrantStatus.PARTIAL_GRANTED), "PARTIAL_GRANTED status should exist")
        assertTrue(statuses.contains(GrantStatus.DENIED), "DENIED status should exist")
        assertTrue(statuses.contains(GrantStatus.DENIED_ALWAYS), "DENIED_ALWAYS status should exist")
        assertTrue(statuses.contains(GrantStatus.NOT_DETERMINED), "NOT_DETERMINED status should exist")
    }

    @Test
    fun testStatusEnumNames() {
        assertEquals("GRANTED", GrantStatus.GRANTED.name)
        assertEquals("PARTIAL_GRANTED", GrantStatus.PARTIAL_GRANTED.name)
        assertEquals("DENIED", GrantStatus.DENIED.name)
        assertEquals("DENIED_ALWAYS", GrantStatus.DENIED_ALWAYS.name)
        assertEquals("NOT_DETERMINED", GrantStatus.NOT_DETERMINED.name)
    }

    @Test
    fun testStatusEnumOrdinals() {
        assertEquals(0, GrantStatus.GRANTED.ordinal)
        assertEquals(1, GrantStatus.PARTIAL_GRANTED.ordinal)
        assertEquals(2, GrantStatus.DENIED.ordinal)
        assertEquals(3, GrantStatus.DENIED_ALWAYS.ordinal)
        assertEquals(4, GrantStatus.NOT_DETERMINED.ordinal)
    }

    @Test
    fun testStatusValueOf() {
        assertEquals(GrantStatus.GRANTED, GrantStatus.valueOf("GRANTED"))
        assertEquals(GrantStatus.PARTIAL_GRANTED, GrantStatus.valueOf("PARTIAL_GRANTED"))
        assertEquals(GrantStatus.DENIED, GrantStatus.valueOf("DENIED"))
        assertEquals(GrantStatus.DENIED_ALWAYS, GrantStatus.valueOf("DENIED_ALWAYS"))
        assertEquals(GrantStatus.NOT_DETERMINED, GrantStatus.valueOf("NOT_DETERMINED"))
    }
}
