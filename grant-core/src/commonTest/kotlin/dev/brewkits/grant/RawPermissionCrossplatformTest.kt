package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform RawPermission tests.
 *
 * RawPermission allows apps to declare custom or platform-specific permissions
 * outside the AppGrant enum. These tests verify the contract holds on all platforms.
 */
class RawPermissionCrossplatformTest {

    private val manager = FakeGrantManager()

    // ====================================================================
    // Group 1: Identity & Contract
    // ====================================================================

    @Test
    fun `RawPermission identifier is preserved correctly`() {
        val perm = RawPermission(
            identifier = "MY_CUSTOM_PERMISSION",
            androidPermissions = listOf("com.example.permission.CUSTOM"),
            iosUsageKey = "NSCustomUsageDescription"
        )
        assertEquals("MY_CUSTOM_PERMISSION", perm.identifier)
    }

    @Test
    fun `RawPermission with null iosUsageKey does not crash on creation`() {
        val perm = RawPermission(
            identifier = "ANDROID_ONLY",
            androidPermissions = listOf("android.permission.VIBRATE"),
            iosUsageKey = null
        )
        assertNotNull(perm)
        assertEquals("ANDROID_ONLY", perm.identifier)
    }

    @Test
    fun `RawPermission with empty androidPermissions is valid`() {
        val perm = RawPermission(
            identifier = "IOS_ONLY",
            androidPermissions = emptyList(),
            iosUsageKey = "NSCameraUsageDescription"
        )
        assertNotNull(perm)
        assertTrue(perm.androidPermissions.isEmpty())
    }

    @Test
    fun `RawPermission with multiple Android permissions is valid`() {
        val perm = RawPermission(
            identifier = "MULTI_ANDROID",
            androidPermissions = listOf(
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS"
            ),
            iosUsageKey = "NSContactsUsageDescription"
        )
        assertEquals(2, perm.androidPermissions.size)
    }

    // ====================================================================
    // Group 2: FakeGrantManager integration
    // ====================================================================

    @Test
    fun `RawPermission checkStatus via FakeGrantManager returns configured status`() = runTest {
        val perm = RawPermission(
            identifier = "CUSTOM_SENSOR",
            androidPermissions = listOf("android.permission.BODY_SENSORS"),
            iosUsageKey = "NSMotionUsageDescription"
        )
        manager.setStatus(perm, GrantStatus.GRANTED)
        assertEquals(GrantStatus.GRANTED, manager.checkStatus(perm))
    }

    @Test
    fun `RawPermission request via FakeGrantManager returns configured result`() = runTest {
        val perm = RawPermission(
            identifier = "CUSTOM_SENSOR",
            androidPermissions = listOf("android.permission.BODY_SENSORS"),
            iosUsageKey = null
        )
        manager.setRequestResult(perm, GrantStatus.DENIED_ALWAYS)
        assertEquals(GrantStatus.DENIED_ALWAYS, manager.request(perm))
    }

    @Test
    fun `RawPermission falls back to global mockStatus if not individually configured`() = runTest {
        manager.mockStatus = GrantStatus.DENIED
        val perm = RawPermission(
            identifier = "UNCONFIGURED",
            androidPermissions = listOf("android.permission.CAMERA"),
            iosUsageKey = null
        )
        assertEquals(GrantStatus.DENIED, manager.checkStatus(perm))
    }

    @Test
    fun `RawPermission is independent from AppGrant with same underlying permission`() = runTest {
        val rawCamera = RawPermission(
            identifier = "RAW_CAMERA",
            androidPermissions = listOf("android.permission.CAMERA"),
            iosUsageKey = "NSCameraUsageDescription"
        )
        manager.setStatus(rawCamera, GrantStatus.DENIED_ALWAYS)
        manager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)

        assertEquals(GrantStatus.DENIED_ALWAYS, manager.checkStatus(rawCamera))
        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.CAMERA))
    }

    // ====================================================================
    // Group 3: Batch request with mixed AppGrant + RawPermission
    // ====================================================================

    @Test
    fun `batch request works with mixed AppGrant and RawPermission`() = runTest {
        val rawPerm = RawPermission(
            identifier = "CUSTOM_HEALTH",
            androidPermissions = listOf("android.permission.BODY_SENSORS"),
            iosUsageKey = "NSHealthShareUsageDescription"
        )
        manager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        manager.setRequestResult(rawPerm, GrantStatus.DENIED)

        val results = manager.request(listOf<GrantPermission>(AppGrant.CAMERA, rawPerm))
        assertEquals(GrantStatus.GRANTED, results[AppGrant.CAMERA])
        assertEquals(GrantStatus.DENIED, results[rawPerm])
    }

    // ====================================================================
    // Group 4: PARTIAL_GRANTED on RawPermission
    // ====================================================================

    @Test
    fun `RawPermission supports PARTIAL_GRANTED status`() = runTest {
        val perm = RawPermission(
            identifier = "LIMITED_PHOTOS",
            androidPermissions = listOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"),
            iosUsageKey = "NSPhotoLibraryUsageDescription"
        )
        manager.configure(perm, GrantStatus.PARTIAL_GRANTED)
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.checkStatus(perm))
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.request(perm))
    }

    // ====================================================================
    // Group 5: Identifier uniqueness
    // ====================================================================

    @Test
    fun `two RawPermissions with different identifiers are independent`() = runTest {
        val permA = RawPermission(identifier = "PERM_A", androidPermissions = emptyList(), iosUsageKey = null)
        val permB = RawPermission(identifier = "PERM_B", androidPermissions = emptyList(), iosUsageKey = null)

        manager.setStatus(permA, GrantStatus.GRANTED)
        manager.setStatus(permB, GrantStatus.DENIED)

        assertEquals(GrantStatus.GRANTED, manager.checkStatus(permA))
        assertEquals(GrantStatus.DENIED, manager.checkStatus(permB))
    }
}
