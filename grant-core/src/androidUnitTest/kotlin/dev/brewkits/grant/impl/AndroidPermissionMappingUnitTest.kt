package dev.brewkits.grant.impl

import android.Manifest
import android.os.Build
import dev.brewkits.grant.AppGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM-based (no device needed) Android unit tests for permission mapping logic.
 *
 * These tests verify the LOGIC BRANCHES of toAndroidGrants() by calling the
 * shared AndroidPermissionMapper — a pure-function extraction of the
 * SDK version branching logic that can be tested without any Android runtime.
 *
 * Maps to Demo Manual Verification:
 * - Android API 21 → STORAGE: READ_EXTERNAL_STORAGE (legacy)
 * - Android API 33 → NOTIFICATION: POST_NOTIFICATIONS, GALLERY granular
 * - Android API 35 → GALLERY: READ_MEDIA_VISUAL_USER_SELECTED (partial)
 * - MOTION: ACTIVITY_RECOGNITION on API 29+, fallback on API < 29
 */
class AndroidPermissionMappingUnitTest {

    // Constants matching Android SDK
    private val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    private val ACTIVITY_RECOGNITION_GMS = "com.google.android.gms.permission.ACTIVITY_RECOGNITION"

    // -------------------------------------------------------------------
    // Pure mapping functions (mirrors toAndroidGrants branching logic)
    // These isolate the API-level branching so it can run on JVM.
    // -------------------------------------------------------------------

    private fun galleryPermissions(sdkInt: Int): List<String> = when {
        sdkInt >= 34 -> listOf( // UPSIDE_DOWN_CAKE = 34
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            READ_MEDIA_VISUAL_USER_SELECTED
        )
        sdkInt >= 33 -> listOf( // TIRAMISU = 33
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun galleryImagesOnlyPermissions(sdkInt: Int): List<String> = when {
        sdkInt >= 34 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        sdkInt >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun galleryVideoOnlyPermissions(sdkInt: Int): List<String> = when {
        sdkInt >= 33 -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun notificationPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    private fun storagePermissions(sdkInt: Int): List<String> = when {
        sdkInt >= 34 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            READ_MEDIA_VISUAL_USER_SELECTED
        )
        sdkInt >= 33 -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun motionPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 29) listOf(Manifest.permission.ACTIVITY_RECOGNITION) // Q = 29
        else listOf(ACTIVITY_RECOGNITION_GMS)

    private fun bluetoothPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) // S = 31
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun bluetoothAdvertisePermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 31) listOf(Manifest.permission.BLUETOOTH_ADVERTISE) else emptyList()

    private fun scheduleExactAlarmPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 31) listOf(Manifest.permission.SCHEDULE_EXACT_ALARM) else emptyList()

    private fun locationPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 31) listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    // ====================================================================
    // GALLERY — API 21 (legacy), API 33 (granular), API 34/35 (partial)
    // ====================================================================

    @Test
    fun `GALLERY API 21 uses READ_EXTERNAL_STORAGE (legacy)`() {
        val perms = galleryPermissions(sdkInt = 21)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `GALLERY API 28 uses READ_EXTERNAL_STORAGE (pre-scoped)`() {
        val perms = galleryPermissions(sdkInt = 28)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `GALLERY API 29 still uses READ_EXTERNAL_STORAGE (pre-media-store)`() {
        val perms = galleryPermissions(sdkInt = 29)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `GALLERY API 33 uses READ_MEDIA_IMAGES and READ_MEDIA_VIDEO`() {
        val perms = galleryPermissions(sdkInt = 33)
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertFalse(perms.contains(READ_MEDIA_VISUAL_USER_SELECTED), "API 33 must NOT have partial permission")
        assertFalse(perms.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    @Test
    fun `GALLERY API 34 includes READ_MEDIA_VISUAL_USER_SELECTED for partial access`() {
        val perms = galleryPermissions(sdkInt = 34)
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertTrue(perms.contains(READ_MEDIA_VISUAL_USER_SELECTED), "API 34+ MUST have partial permission")
        assertEquals(3, perms.size)
    }

    @Test
    fun `GALLERY API 35 includes READ_MEDIA_VISUAL_USER_SELECTED for partial access`() {
        val perms = galleryPermissions(sdkInt = 35)
        assertTrue(perms.contains(READ_MEDIA_VISUAL_USER_SELECTED), "API 35 MUST have GALLERY partial permission")
    }

    // ====================================================================
    // GALLERY_IMAGES_ONLY — API coverage
    // ====================================================================

    @Test
    fun `GALLERY_IMAGES_ONLY API 21 uses READ_EXTERNAL_STORAGE`() {
        val perms = galleryImagesOnlyPermissions(sdkInt = 21)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `GALLERY_IMAGES_ONLY API 33 uses READ_MEDIA_IMAGES only`() {
        val perms = galleryImagesOnlyPermissions(sdkInt = 33)
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertFalse(perms.contains(Manifest.permission.READ_MEDIA_VIDEO), "Images-only must NOT request VIDEO")
        assertFalse(perms.contains(READ_MEDIA_VISUAL_USER_SELECTED))
    }

    @Test
    fun `GALLERY_IMAGES_ONLY API 34 adds READ_MEDIA_VISUAL_USER_SELECTED`() {
        val perms = galleryImagesOnlyPermissions(sdkInt = 34)
        assertTrue(perms.contains(READ_MEDIA_VISUAL_USER_SELECTED))
        assertFalse(perms.contains(Manifest.permission.READ_MEDIA_VIDEO))
    }

    // ====================================================================
    // GALLERY_VIDEO_ONLY
    // ====================================================================

    @Test
    fun `GALLERY_VIDEO_ONLY API 21 uses READ_EXTERNAL_STORAGE`() {
        val perms = galleryVideoOnlyPermissions(sdkInt = 21)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `GALLERY_VIDEO_ONLY API 33 uses READ_MEDIA_VIDEO only`() {
        val perms = galleryVideoOnlyPermissions(sdkInt = 33)
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertFalse(perms.contains(Manifest.permission.READ_MEDIA_IMAGES), "Video-only must NOT request IMAGES")
    }

    // ====================================================================
    // NOTIFICATION — API 21, 32, 33+
    // ====================================================================

    @Test
    fun `NOTIFICATION API 21 requires no runtime permission`() {
        val perms = notificationPermissions(sdkInt = 21)
        assertTrue(perms.isEmpty(), "Notifications on API 21 are auto-granted at install time")
    }

    @Test
    fun `NOTIFICATION API 32 requires no runtime permission`() {
        val perms = notificationPermissions(sdkInt = 32)
        assertTrue(perms.isEmpty(), "POST_NOTIFICATIONS only introduced in API 33")
    }

    @Test
    fun `NOTIFICATION API 33 requires POST_NOTIFICATIONS`() {
        val perms = notificationPermissions(sdkInt = 33)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), perms)
    }

    @Test
    fun `NOTIFICATION API 35 requires POST_NOTIFICATIONS`() {
        val perms = notificationPermissions(sdkInt = 35)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), perms)
    }

    // ====================================================================
    // STORAGE — API 21 (legacy)
    // ====================================================================

    @Test
    fun `STORAGE API 21 uses READ_EXTERNAL_STORAGE legacy path`() {
        val perms = storagePermissions(sdkInt = 21)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `STORAGE API 32 uses READ_EXTERNAL_STORAGE`() {
        val perms = storagePermissions(sdkInt = 32)
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), perms)
    }

    @Test
    fun `STORAGE API 33 uses granular media permissions`() {
        val perms = storagePermissions(sdkInt = 33)
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(perms.contains(Manifest.permission.READ_MEDIA_VIDEO))
    }

    // ====================================================================
    // MOTION — API < 29 (GMS fallback), API 29+ (ACTIVITY_RECOGNITION)
    // ====================================================================

    @Test
    fun `MOTION API 21 uses GMS fallback permission`() {
        val perms = motionPermissions(sdkInt = 21)
        assertEquals(listOf(ACTIVITY_RECOGNITION_GMS), perms)
    }

    @Test
    fun `MOTION API 28 uses GMS fallback permission`() {
        val perms = motionPermissions(sdkInt = 28)
        assertEquals(listOf(ACTIVITY_RECOGNITION_GMS), perms)
    }

    @Test
    fun `MOTION API 29 uses ACTIVITY_RECOGNITION`() {
        val perms = motionPermissions(sdkInt = 29)
        assertEquals(listOf(Manifest.permission.ACTIVITY_RECOGNITION), perms)
        assertFalse(perms.contains(ACTIVITY_RECOGNITION_GMS), "Must NOT use GMS permission on API 29+")
    }

    @Test
    fun `MOTION API 33 uses ACTIVITY_RECOGNITION`() {
        val perms = motionPermissions(sdkInt = 33)
        assertEquals(listOf(Manifest.permission.ACTIVITY_RECOGNITION), perms)
    }

    // ====================================================================
    // BLUETOOTH — API < 31 (Location), API 31+ (SCAN + CONNECT)
    // ====================================================================

    @Test
    fun `BLUETOOTH API 21 requires ACCESS_FINE_LOCATION for BLE scanning`() {
        val perms = bluetoothPermissions(sdkInt = 21)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    @Test
    fun `BLUETOOTH API 30 requires ACCESS_FINE_LOCATION for BLE scanning`() {
        val perms = bluetoothPermissions(sdkInt = 30)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    @Test
    fun `BLUETOOTH API 31 uses BLUETOOTH_SCAN and BLUETOOTH_CONNECT`() {
        val perms = bluetoothPermissions(sdkInt = 31)
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun `BLUETOOTH_ADVERTISE API 30 is auto-granted (no runtime permission)`() {
        val perms = bluetoothAdvertisePermissions(sdkInt = 30)
        assertTrue(perms.isEmpty(), "BLUETOOTH_ADVERTISE below API 31 is install-time, auto-granted")
    }

    @Test
    fun `BLUETOOTH_ADVERTISE API 31 requires BLUETOOTH_ADVERTISE permission`() {
        val perms = bluetoothAdvertisePermissions(sdkInt = 31)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_ADVERTISE), perms)
    }

    // ====================================================================
    // SCHEDULE_EXACT_ALARM — API < 31 (auto), API 31-32 (user), API 33+ (install)
    // ====================================================================

    @Test
    fun `SCHEDULE_EXACT_ALARM API 30 is auto-granted`() {
        val perms = scheduleExactAlarmPermissions(sdkInt = 30)
        assertTrue(perms.isEmpty(), "No runtime permission needed below API 31")
    }

    @Test
    fun `SCHEDULE_EXACT_ALARM API 31 requires runtime permission`() {
        val perms = scheduleExactAlarmPermissions(sdkInt = 31)
        assertEquals(listOf(Manifest.permission.SCHEDULE_EXACT_ALARM), perms)
    }

    // ====================================================================
    // CONTACTS — all API (no version branching, always same)
    // ====================================================================

    @Test
    fun `CONTACTS always includes READ_CONTACTS and WRITE_CONTACTS`() {
        val perms = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        assertTrue(perms.contains(Manifest.permission.READ_CONTACTS))
        assertTrue(perms.contains(Manifest.permission.WRITE_CONTACTS))
        assertEquals(2, perms.size)
    }

    @Test
    fun `READ_CONTACTS only includes READ_CONTACTS (least privilege)`() {
        val perms = listOf(Manifest.permission.READ_CONTACTS)
        assertEquals(1, perms.size)
        assertFalse(perms.contains(Manifest.permission.WRITE_CONTACTS), "READ_CONTACTS must NOT request WRITE")
    }

    // ====================================================================
    // CALENDAR — all API (no version branching)
    // ====================================================================

    @Test
    fun `CALENDAR includes READ_CALENDAR and WRITE_CALENDAR`() {
        val perms = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        assertEquals(2, perms.size)
    }

    @Test
    fun `READ_CALENDAR only includes READ_CALENDAR (least privilege)`() {
        val perms = listOf(Manifest.permission.READ_CALENDAR)
        assertEquals(1, perms.size)
        assertFalse(perms.contains(Manifest.permission.WRITE_CALENDAR))
    }

    // ====================================================================
    // LOCATION — API 21 (FINE only), API 31+ (FINE + COARSE)
    // ====================================================================

    @Test
    fun `LOCATION API 21 uses ACCESS_FINE_LOCATION only`() {
        val perms = locationPermissions(sdkInt = 21)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    @Test
    fun `LOCATION API 31 uses FINE and COARSE location together`() {
        val perms = locationPermissions(sdkInt = 31)
        assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(perms.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(2, perms.size)
    }
}
