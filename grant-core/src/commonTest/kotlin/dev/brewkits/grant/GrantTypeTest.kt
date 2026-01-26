package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantTypeTest {

    @Test
    fun testAllGrantTypesExist() {
        val grants = AppGrant.entries
        assertEquals(10, grants.size, "Expected 10 grant types")
    }

    @Test
    fun testIndividualGrantTypes() {
        val grants = AppGrant.entries

        assertTrue(grants.contains(AppGrant.CAMERA), "CAMERA grant should exist")
        assertTrue(grants.contains(AppGrant.GALLERY), "GALLERY grant should exist")
        assertTrue(grants.contains(AppGrant.STORAGE), "STORAGE grant should exist")
        assertTrue(grants.contains(AppGrant.LOCATION), "LOCATION grant should exist")
        assertTrue(grants.contains(AppGrant.LOCATION_ALWAYS), "LOCATION_ALWAYS grant should exist")
        assertTrue(grants.contains(AppGrant.NOTIFICATION), "NOTIFICATION grant should exist")
        assertTrue(grants.contains(AppGrant.BLUETOOTH), "BLUETOOTH grant should exist")
        assertTrue(grants.contains(AppGrant.MICROPHONE), "MICROPHONE grant should exist")
        assertTrue(grants.contains(AppGrant.CONTACTS), "CONTACTS grant should exist")
        assertTrue(grants.contains(AppGrant.MOTION), "MOTION grant should exist")
    }

    @Test
    fun testGrantEnumNames() {
        assertEquals("CAMERA", AppGrant.CAMERA.name)
        assertEquals("GALLERY", AppGrant.GALLERY.name)
        assertEquals("STORAGE", AppGrant.STORAGE.name)
        assertEquals("LOCATION", AppGrant.LOCATION.name)
        assertEquals("LOCATION_ALWAYS", AppGrant.LOCATION_ALWAYS.name)
        assertEquals("NOTIFICATION", AppGrant.NOTIFICATION.name)
        assertEquals("BLUETOOTH", AppGrant.BLUETOOTH.name)
        assertEquals("MICROPHONE", AppGrant.MICROPHONE.name)
        assertEquals("CONTACTS", AppGrant.CONTACTS.name)
        assertEquals("MOTION", AppGrant.MOTION.name)
    }

    @Test
    fun testGrantEnumOrdinals() {
        assertEquals(0, AppGrant.CAMERA.ordinal)
        assertEquals(1, AppGrant.GALLERY.ordinal)
        assertEquals(2, AppGrant.STORAGE.ordinal)
        assertEquals(3, AppGrant.LOCATION.ordinal)
        assertEquals(4, AppGrant.LOCATION_ALWAYS.ordinal)
        assertEquals(5, AppGrant.NOTIFICATION.ordinal)
        assertEquals(6, AppGrant.BLUETOOTH.ordinal)
        assertEquals(7, AppGrant.MICROPHONE.ordinal)
        assertEquals(8, AppGrant.CONTACTS.ordinal)
        assertEquals(9, AppGrant.MOTION.ordinal)
    }

    @Test
    fun testGrantValueOf() {
        assertEquals(AppGrant.CAMERA, AppGrant.valueOf("CAMERA"))
        assertEquals(AppGrant.GALLERY, AppGrant.valueOf("GALLERY"))
        assertEquals(AppGrant.STORAGE, AppGrant.valueOf("STORAGE"))
        assertEquals(AppGrant.LOCATION, AppGrant.valueOf("LOCATION"))
        assertEquals(AppGrant.LOCATION_ALWAYS, AppGrant.valueOf("LOCATION_ALWAYS"))
        assertEquals(AppGrant.NOTIFICATION, AppGrant.valueOf("NOTIFICATION"))
        assertEquals(AppGrant.BLUETOOTH, AppGrant.valueOf("BLUETOOTH"))
        assertEquals(AppGrant.MICROPHONE, AppGrant.valueOf("MICROPHONE"))
        assertEquals(AppGrant.CONTACTS, AppGrant.valueOf("CONTACTS"))
        assertEquals(AppGrant.MOTION, AppGrant.valueOf("MOTION"))
    }
}
