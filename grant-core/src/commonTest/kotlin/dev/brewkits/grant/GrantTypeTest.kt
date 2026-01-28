package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantTypeTest {

    @Test
    fun testAllGrantTypesExist() {
        val grants = AppGrant.entries
        assertEquals(14, grants.size, "Expected 14 grant types")
    }

    @Test
    fun testIndividualGrantTypes() {
        val grants = AppGrant.entries

        assertTrue(grants.contains(AppGrant.CAMERA), "CAMERA grant should exist")
        assertTrue(grants.contains(AppGrant.GALLERY), "GALLERY grant should exist")
        assertTrue(grants.contains(AppGrant.GALLERY_IMAGES_ONLY), "GALLERY_IMAGES_ONLY grant should exist")
        assertTrue(grants.contains(AppGrant.GALLERY_VIDEO_ONLY), "GALLERY_VIDEO_ONLY grant should exist")
        assertTrue(grants.contains(AppGrant.STORAGE), "STORAGE grant should exist")
        assertTrue(grants.contains(AppGrant.LOCATION), "LOCATION grant should exist")
        assertTrue(grants.contains(AppGrant.LOCATION_ALWAYS), "LOCATION_ALWAYS grant should exist")
        assertTrue(grants.contains(AppGrant.NOTIFICATION), "NOTIFICATION grant should exist")
        assertTrue(grants.contains(AppGrant.SCHEDULE_EXACT_ALARM), "SCHEDULE_EXACT_ALARM grant should exist")
        assertTrue(grants.contains(AppGrant.BLUETOOTH), "BLUETOOTH grant should exist")
        assertTrue(grants.contains(AppGrant.MICROPHONE), "MICROPHONE grant should exist")
        assertTrue(grants.contains(AppGrant.CONTACTS), "CONTACTS grant should exist")
        assertTrue(grants.contains(AppGrant.MOTION), "MOTION grant should exist")
        assertTrue(grants.contains(AppGrant.CALENDAR), "CALENDAR grant should exist")
    }

    @Test
    fun testGrantEnumNames() {
        assertEquals("CAMERA", AppGrant.CAMERA.name)
        assertEquals("GALLERY", AppGrant.GALLERY.name)
        assertEquals("GALLERY_IMAGES_ONLY", AppGrant.GALLERY_IMAGES_ONLY.name)
        assertEquals("GALLERY_VIDEO_ONLY", AppGrant.GALLERY_VIDEO_ONLY.name)
        assertEquals("STORAGE", AppGrant.STORAGE.name)
        assertEquals("LOCATION", AppGrant.LOCATION.name)
        assertEquals("LOCATION_ALWAYS", AppGrant.LOCATION_ALWAYS.name)
        assertEquals("NOTIFICATION", AppGrant.NOTIFICATION.name)
        assertEquals("SCHEDULE_EXACT_ALARM", AppGrant.SCHEDULE_EXACT_ALARM.name)
        assertEquals("BLUETOOTH", AppGrant.BLUETOOTH.name)
        assertEquals("MICROPHONE", AppGrant.MICROPHONE.name)
        assertEquals("CONTACTS", AppGrant.CONTACTS.name)
        assertEquals("MOTION", AppGrant.MOTION.name)
        assertEquals("CALENDAR", AppGrant.CALENDAR.name)
    }

    @Test
    fun testGrantEnumOrdinals() {
        assertEquals(0, AppGrant.CAMERA.ordinal)
        assertEquals(1, AppGrant.GALLERY.ordinal)
        assertEquals(2, AppGrant.GALLERY_IMAGES_ONLY.ordinal)
        assertEquals(3, AppGrant.GALLERY_VIDEO_ONLY.ordinal)
        assertEquals(4, AppGrant.STORAGE.ordinal)
        assertEquals(5, AppGrant.LOCATION.ordinal)
        assertEquals(6, AppGrant.LOCATION_ALWAYS.ordinal)
        assertEquals(7, AppGrant.NOTIFICATION.ordinal)
        assertEquals(8, AppGrant.SCHEDULE_EXACT_ALARM.ordinal)
        assertEquals(9, AppGrant.BLUETOOTH.ordinal)
        assertEquals(10, AppGrant.MICROPHONE.ordinal)
        assertEquals(11, AppGrant.CONTACTS.ordinal)
        assertEquals(12, AppGrant.MOTION.ordinal)
        assertEquals(13, AppGrant.CALENDAR.ordinal)
    }

    @Test
    fun testGrantValueOf() {
        assertEquals(AppGrant.CAMERA, AppGrant.valueOf("CAMERA"))
        assertEquals(AppGrant.GALLERY, AppGrant.valueOf("GALLERY"))
        assertEquals(AppGrant.GALLERY_IMAGES_ONLY, AppGrant.valueOf("GALLERY_IMAGES_ONLY"))
        assertEquals(AppGrant.GALLERY_VIDEO_ONLY, AppGrant.valueOf("GALLERY_VIDEO_ONLY"))
        assertEquals(AppGrant.STORAGE, AppGrant.valueOf("STORAGE"))
        assertEquals(AppGrant.LOCATION, AppGrant.valueOf("LOCATION"))
        assertEquals(AppGrant.LOCATION_ALWAYS, AppGrant.valueOf("LOCATION_ALWAYS"))
        assertEquals(AppGrant.NOTIFICATION, AppGrant.valueOf("NOTIFICATION"))
        assertEquals(AppGrant.SCHEDULE_EXACT_ALARM, AppGrant.valueOf("SCHEDULE_EXACT_ALARM"))
        assertEquals(AppGrant.BLUETOOTH, AppGrant.valueOf("BLUETOOTH"))
        assertEquals(AppGrant.MICROPHONE, AppGrant.valueOf("MICROPHONE"))
        assertEquals(AppGrant.CONTACTS, AppGrant.valueOf("CONTACTS"))
        assertEquals(AppGrant.MOTION, AppGrant.valueOf("MOTION"))
        assertEquals(AppGrant.CALENDAR, AppGrant.valueOf("CALENDAR"))
    }
}
