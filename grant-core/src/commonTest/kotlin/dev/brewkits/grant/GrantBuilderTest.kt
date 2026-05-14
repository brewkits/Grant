package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Cross-platform tests for the opt-in handler-registration DSL.
 *
 * These tests only inspect the registry produced by [GrantBuilder]; they do
 * not exercise the platform delegate. That keeps them runnable on Android,
 * iOS, and JVM common tests without needing platform setup.
 */
class GrantBuilderTest {

    @Test
    fun emptyBlockProducesEmptyRegistry() {
        val manager = GrantFactory.create { /* no permissions */ }
        assertNotNull(manager, "GrantManager should be returned even for empty block")
    }

    @Test
    fun registerAllRegistersEveryAppGrant() {
        // Use the builder directly so we can inspect the registry contents.
        val builder = GrantBuilder().apply { registerAll() }
        val registered = builder.registrations.keys

        AppGrant.entries.forEach { grant ->
            assertTrue(
                grant in registered,
                "registerAll() must register every AppGrant; missing: ${grant.name}"
            )
        }
    }

    @Test
    fun selectiveRegistrationOnlyAddsRequestedPermissions() {
        val builder = GrantBuilder().apply {
            location()
            bluetooth()
        }

        assertTrue(AppGrant.LOCATION in builder.registrations)
        assertTrue(AppGrant.BLUETOOTH in builder.registrations)
        assertTrue(AppGrant.BLUETOOTH_ADVERTISE in builder.registrations)

        // Permissions we didn't ask for must NOT appear in the registry.
        assertFalse(AppGrant.CAMERA in builder.registrations)
        assertFalse(AppGrant.CONTACTS in builder.registrations)
        assertFalse(AppGrant.MOTION in builder.registrations)
        assertFalse(AppGrant.CALENDAR in builder.registrations)
    }

    @Test
    fun bluetoothExtensionCoversAdvertise() {
        val builder = GrantBuilder().apply { bluetooth() }
        assertTrue(AppGrant.BLUETOOTH in builder.registrations)
        assertTrue(
            AppGrant.BLUETOOTH_ADVERTISE in builder.registrations,
            "bluetooth() must also register BLUETOOTH_ADVERTISE since both share the same handler"
        )
    }

    @Test
    fun contactsExtensionCoversReadContacts() {
        val builder = GrantBuilder().apply { contacts() }
        assertTrue(AppGrant.CONTACTS in builder.registrations)
        assertTrue(AppGrant.READ_CONTACTS in builder.registrations)
    }

    @Test
    fun calendarExtensionCoversReadCalendar() {
        val builder = GrantBuilder().apply { calendar() }
        assertTrue(AppGrant.CALENDAR in builder.registrations)
        assertTrue(AppGrant.READ_CALENDAR in builder.registrations)
    }

    @Test
    fun galleryExtensionCoversAllGalleryVariants() {
        val builder = GrantBuilder().apply { gallery() }
        assertTrue(AppGrant.GALLERY in builder.registrations)
        assertTrue(AppGrant.GALLERY_IMAGES_ONLY in builder.registrations)
        assertTrue(AppGrant.GALLERY_VIDEO_ONLY in builder.registrations)
    }

    @Test
    fun registerAllAppGrantCount() {
        val builder = GrantBuilder().apply { registerAll() }
        assertEquals(
            AppGrant.entries.size,
            builder.registrations.size,
            "registerAll() should produce one registration per AppGrant"
        )
    }
}
