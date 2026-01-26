package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantStatusTest {

    @Test
    fun testAllGrantStatusesExist() {
        val statuses = GrantStatus.entries
        assertEquals(4, statuses.size, "Expected 4 grant statuses")
    }

    @Test
    fun testIndividualStatuses() {
        val statuses = GrantStatus.entries

        assertTrue(statuses.contains(GrantStatus.GRANTED), "GRANTED status should exist")
        assertTrue(statuses.contains(GrantStatus.DENIED), "DENIED status should exist")
        assertTrue(statuses.contains(GrantStatus.DENIED_ALWAYS), "DENIED_ALWAYS status should exist")
        assertTrue(statuses.contains(GrantStatus.NOT_DETERMINED), "NOT_DETERMINED status should exist")
    }

    @Test
    fun testStatusEnumNames() {
        assertEquals("GRANTED", GrantStatus.GRANTED.name)
        assertEquals("DENIED", GrantStatus.DENIED.name)
        assertEquals("DENIED_ALWAYS", GrantStatus.DENIED_ALWAYS.name)
        assertEquals("NOT_DETERMINED", GrantStatus.NOT_DETERMINED.name)
    }

    @Test
    fun testStatusEnumOrdinals() {
        assertEquals(0, GrantStatus.GRANTED.ordinal)
        assertEquals(1, GrantStatus.DENIED.ordinal)
        assertEquals(2, GrantStatus.DENIED_ALWAYS.ordinal)
        assertEquals(3, GrantStatus.NOT_DETERMINED.ordinal)
    }

    @Test
    fun testStatusValueOf() {
        assertEquals(GrantStatus.GRANTED, GrantStatus.valueOf("GRANTED"))
        assertEquals(GrantStatus.DENIED, GrantStatus.valueOf("DENIED"))
        assertEquals(GrantStatus.DENIED_ALWAYS, GrantStatus.valueOf("DENIED_ALWAYS"))
        assertEquals(GrantStatus.NOT_DETERMINED, GrantStatus.valueOf("NOT_DETERMINED"))
    }
}
