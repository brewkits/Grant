package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrantTypeTest {

    @Test
    fun testAllGrantTypesExist() {
        val grants = AppGrant.entries
        assertEquals(20, grants.size, "Expected 20 grant types")
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
        assertTrue(grants.contains(AppGrant.BLUETOOTH_ADVERTISE), "BLUETOOTH_ADVERTISE grant should exist")
        assertTrue(grants.contains(AppGrant.MICROPHONE), "MICROPHONE grant should exist")
        assertTrue(grants.contains(AppGrant.CONTACTS), "CONTACTS grant should exist")
        assertTrue(grants.contains(AppGrant.READ_CONTACTS), "READ_CONTACTS grant should exist")
        assertTrue(grants.contains(AppGrant.MOTION), "MOTION grant should exist")
        assertTrue(grants.contains(AppGrant.CALENDAR), "CALENDAR grant should exist")
        assertTrue(grants.contains(AppGrant.READ_CALENDAR), "READ_CALENDAR grant should exist")
        assertTrue(grants.contains(AppGrant.NEARBY_WIFI_DEVICES), "NEARBY_WIFI_DEVICES grant should exist")
        assertTrue(grants.contains(AppGrant.LOCAL_NETWORK), "LOCAL_NETWORK grant should exist")
        assertTrue(grants.contains(AppGrant.GALLERY_ADD_ONLY), "GALLERY_ADD_ONLY grant should exist")
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
        assertEquals("BLUETOOTH_ADVERTISE", AppGrant.BLUETOOTH_ADVERTISE.name)
        assertEquals("MICROPHONE", AppGrant.MICROPHONE.name)
        assertEquals("CONTACTS", AppGrant.CONTACTS.name)
        assertEquals("READ_CONTACTS", AppGrant.READ_CONTACTS.name)
        assertEquals("MOTION", AppGrant.MOTION.name)
        assertEquals("CALENDAR", AppGrant.CALENDAR.name)
        assertEquals("READ_CALENDAR", AppGrant.READ_CALENDAR.name)
        assertEquals("NEARBY_WIFI_DEVICES", AppGrant.NEARBY_WIFI_DEVICES.name)
        assertEquals("GALLERY_ADD_ONLY", AppGrant.GALLERY_ADD_ONLY.name)
    }

    /**
     * Pins the current declaration order.
     *
     * **Ordinals are not a compatibility contract.** Nothing persists or transmits them:
     * [SharedPreferencesGrantStore] keys its request history off `AppGrant.name`, and no
     * production code reads `.ordinal` or indexes `entries`. A new grant may therefore be
     * inserted wherever it reads best — `GALLERY_ADD_ONLY` sits beside the other gallery
     * grants rather than being appended — and updating this test is the whole cost of
     * doing so. Do not move an entry to avoid touching this list.
     */
    @Test
    fun testGrantEnumOrdinals() {
        assertEquals(0, AppGrant.CAMERA.ordinal)
        assertEquals(1, AppGrant.GALLERY.ordinal)
        assertEquals(2, AppGrant.GALLERY_IMAGES_ONLY.ordinal)
        assertEquals(3, AppGrant.GALLERY_VIDEO_ONLY.ordinal)
        assertEquals(4, AppGrant.GALLERY_ADD_ONLY.ordinal)
        assertEquals(5, AppGrant.STORAGE.ordinal)
        assertEquals(6, AppGrant.LOCATION.ordinal)
        assertEquals(7, AppGrant.LOCATION_ALWAYS.ordinal)
        assertEquals(8, AppGrant.NOTIFICATION.ordinal)
        assertEquals(9, AppGrant.SCHEDULE_EXACT_ALARM.ordinal)
        assertEquals(10, AppGrant.BLUETOOTH.ordinal)
        assertEquals(11, AppGrant.BLUETOOTH_ADVERTISE.ordinal)
        assertEquals(12, AppGrant.MICROPHONE.ordinal)
        assertEquals(13, AppGrant.CONTACTS.ordinal)
        assertEquals(14, AppGrant.READ_CONTACTS.ordinal)
        assertEquals(15, AppGrant.MOTION.ordinal)
        assertEquals(16, AppGrant.CALENDAR.ordinal)
        assertEquals(17, AppGrant.READ_CALENDAR.ordinal)
        assertEquals(18, AppGrant.NEARBY_WIFI_DEVICES.ordinal)
        assertEquals(19, AppGrant.LOCAL_NETWORK.ordinal)
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
        assertEquals(AppGrant.BLUETOOTH_ADVERTISE, AppGrant.valueOf("BLUETOOTH_ADVERTISE"))
        assertEquals(AppGrant.MICROPHONE, AppGrant.valueOf("MICROPHONE"))
        assertEquals(AppGrant.CONTACTS, AppGrant.valueOf("CONTACTS"))
        assertEquals(AppGrant.READ_CONTACTS, AppGrant.valueOf("READ_CONTACTS"))
        assertEquals(AppGrant.MOTION, AppGrant.valueOf("MOTION"))
        assertEquals(AppGrant.CALENDAR, AppGrant.valueOf("CALENDAR"))
        assertEquals(AppGrant.READ_CALENDAR, AppGrant.valueOf("READ_CALENDAR"))
        assertEquals(AppGrant.NEARBY_WIFI_DEVICES, AppGrant.valueOf("NEARBY_WIFI_DEVICES"))
    }
}
