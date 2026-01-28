package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for GrantPermission extensibility system.
 */
class GrantPermissionTest {

    @Test
    fun `AppGrant implements GrantPermission`() {
        val camera: GrantPermission = AppGrant.CAMERA

        assertTrue(camera is GrantPermission, "AppGrant should implement GrantPermission")
        assertEquals("CAMERA", camera.identifier)
    }

    @Test
    fun `AppGrant identifier uses enum name`() {
        assertEquals("CAMERA", AppGrant.CAMERA.identifier)
        assertEquals("LOCATION", AppGrant.LOCATION.identifier)
        assertEquals("MICROPHONE", AppGrant.MICROPHONE.identifier)
        assertEquals("GALLERY", AppGrant.GALLERY.identifier)
    }

    @Test
    fun `RawPermission - Android-only permission`() {
        val customPermission = RawPermission(
            identifier = "CUSTOM_SENSOR",
            androidPermissions = listOf("android.permission.CUSTOM_SENSOR"),
            iosUsageKey = null
        )

        assertEquals("CUSTOM_SENSOR", customPermission.identifier)
        assertEquals(1, customPermission.androidPermissions.size)
        assertEquals("android.permission.CUSTOM_SENSOR", customPermission.androidPermissions[0])
        assertEquals(null, customPermission.iosUsageKey)
    }

    @Test
    fun `RawPermission - iOS-only permission`() {
        val healthKit = RawPermission(
            identifier = "HEALTH_KIT",
            androidPermissions = emptyList(),
            iosUsageKey = "NSHealthShareUsageDescription"
        )

        assertEquals("HEALTH_KIT", healthKit.identifier)
        assertTrue(healthKit.androidPermissions.isEmpty())
        assertEquals("NSHealthShareUsageDescription", healthKit.iosUsageKey)
    }

    @Test
    fun `RawPermission - Cross-platform permission`() {
        val biometric = RawPermission(
            identifier = "BIOMETRIC",
            androidPermissions = listOf("android.permission.USE_BIOMETRIC"),
            iosUsageKey = "NSFaceIDUsageDescription"
        )

        assertEquals("BIOMETRIC", biometric.identifier)
        assertEquals(1, biometric.androidPermissions.size)
        assertEquals("android.permission.USE_BIOMETRIC", biometric.androidPermissions[0])
        assertEquals("NSFaceIDUsageDescription", biometric.iosUsageKey)
    }

    @Test
    fun `RawPermission - Multiple Android permissions`() {
        val locationCustom = RawPermission(
            identifier = "LOCATION_CUSTOM",
            androidPermissions = listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION"
            ),
            iosUsageKey = "NSLocationAlwaysAndWhenInUseUsageDescription"
        )

        assertEquals("LOCATION_CUSTOM", locationCustom.identifier)
        assertEquals(3, locationCustom.androidPermissions.size)
    }

    @Test
    fun `RawPermission - data class equality`() {
        val permission1 = RawPermission(
            identifier = "TEST",
            androidPermissions = listOf("android.permission.TEST"),
            iosUsageKey = "NSTestUsageDescription"
        )

        val permission2 = RawPermission(
            identifier = "TEST",
            androidPermissions = listOf("android.permission.TEST"),
            iosUsageKey = "NSTestUsageDescription"
        )

        assertEquals(permission1, permission2, "Data classes with same values should be equal")
    }

    @Test
    fun `GrantPermission - polymorphic list`() {
        val permissions: List<GrantPermission> = listOf(
            AppGrant.CAMERA,
            AppGrant.LOCATION,
            RawPermission(
                identifier = "CUSTOM",
                androidPermissions = listOf("android.permission.CUSTOM"),
                iosUsageKey = null
            )
        )

        assertEquals(3, permissions.size)
        assertEquals("CAMERA", permissions[0].identifier)
        assertEquals("LOCATION", permissions[1].identifier)
        assertEquals("CUSTOM", permissions[2].identifier)
    }

    @Test
    fun `GrantPermission - type checking`() {
        val appGrant: GrantPermission = AppGrant.CAMERA
        val rawPermission: GrantPermission = RawPermission(
            identifier = "CUSTOM",
            androidPermissions = emptyList(),
            iosUsageKey = null
        )

        assertTrue(appGrant is AppGrant)
        assertTrue(rawPermission is RawPermission)
    }
}
