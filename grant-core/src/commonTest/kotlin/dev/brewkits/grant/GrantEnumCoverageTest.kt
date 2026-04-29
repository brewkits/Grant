package dev.brewkits.grant

import kotlin.test.*

class GrantEnumCoverageTest {

    @Test
    fun `test GrantStatus values`() {
        GrantStatus.entries.forEach { status ->
            assertNotNull(status.name)
        }
    }

    @Test
    fun `test AppGrant values and identifiers`() {
        AppGrant.entries.forEach { grant ->
            assertNotNull(grant.identifier)
            // Verify it can be used as GrantPermission
            val perm: GrantPermission = grant
            assertEquals(grant.identifier, perm.identifier)
        }
    }

    @Test
    fun `test RawPermission`() {
        val raw = RawPermission(
            identifier = "CUSTOM",
            androidPermissions = listOf("p1", "p2"),
            iosUsageKey = "key"
        )
        assertEquals("CUSTOM", raw.identifier)
        assertEquals(listOf("p1", "p2"), raw.androidPermissions)
        assertEquals("key", raw.iosUsageKey)
    }
}
