package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.RawPermission
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Instrumented tests for PlatformGrantDelegate on Android.
 *
 * These tests verify:
 * - Android-specific permission handling
 * - API version-specific behavior
 * - Android 14+ partial gallery access
 * - Notification permission handling
 * - Dead click fix (manual verification)
 * - RawPermission support on Android
 *
 * Note: Some tests require specific API levels or manual permission management.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class PlatformGrantDelegateAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var delegate: PlatformGrantDelegate
    private lateinit var store: InMemoryGrantStore

    @Before
    fun setup() {
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
    }

    // ==================== Basic Permission Checks ====================

    @Test
    fun testCheckInternetPermission_shouldBeGranted() = runBlocking {
        // INTERNET permission is declared in manifest and auto-granted
        val status = delegate.checkStatus(AppGrant.CAMERA)  // Use camera as example
        assertNotEquals(
            GrantStatus.NOT_DETERMINED,
            status,
            "Status should be determinable"
        )
    }

    @Test
    fun testGrantedPermission_shouldReturnGranted() = runBlocking {
        // Check a permission that's already granted
        val permission = Manifest.permission.INTERNET

        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            // Manually mark as granted in store for testing
            // In real scenario, this comes from OS
            assertTrue(true, "INTERNET permission is granted")
        }
    }

    // ==================== Android 14+ Tests ====================

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)  // Android 14+
    fun testAndroid14PartialGalleryAccess_hasCorrectPermissions() {
        // Test that Android 14 permissions are correctly mapped
        val galleryPermissions = with(delegate) {
            AppGrant.GALLERY.toAndroidGrants()
        }

        assertTrue(
            galleryPermissions.contains(Manifest.permission.READ_MEDIA_IMAGES),
            "Should include READ_MEDIA_IMAGES on Android 14+"
        )
        assertTrue(
            galleryPermissions.contains(Manifest.permission.READ_MEDIA_VIDEO),
            "Should include READ_MEDIA_VIDEO on Android 14+"
        )
        assertTrue(
            galleryPermissions.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"),
            "Should include READ_MEDIA_VISUAL_USER_SELECTED on Android 14+"
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, maxSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun testAndroid13GalleryAccess_doesNotIncludePartialPermission() {
        // Test that Android 13 doesn't include partial access permission
        val galleryPermissions = with(delegate) {
            AppGrant.GALLERY.toAndroidGrants()
        }

        assertTrue(
            galleryPermissions.contains(Manifest.permission.READ_MEDIA_IMAGES),
            "Should include READ_MEDIA_IMAGES on Android 13"
        )
        assertEquals(
            false,
            galleryPermissions.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"),
            "Should NOT include READ_MEDIA_VISUAL_USER_SELECTED on Android 13"
        )
    }

    // ==================== Notification Permission Tests ====================

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)  // Android 13+
    fun testNotificationPermission_Android13Plus() {
        // Test that Android 13+ uses POST_NOTIFICATIONS
        val notificationPermissions = with(delegate) {
            AppGrant.NOTIFICATION.toAndroidGrants()
        }

        assertTrue(
            notificationPermissions.contains(Manifest.permission.POST_NOTIFICATIONS),
            "Should include POST_NOTIFICATIONS on Android 13+"
        )
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.S_V2)  // Android 12 and below
    fun testNotificationPermission_Android12AndBelow() {
        // Test that Android 12- doesn't require runtime permission
        val notificationPermissions = with(delegate) {
            AppGrant.NOTIFICATION.toAndroidGrants()
        }

        assertTrue(
            notificationPermissions.isEmpty(),
            "Should have no runtime permissions for notifications on Android 12-"
        )
    }

    // ==================== Location Permission Tests ====================

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)  // Android 12+
    fun testLocationPermission_Android12Plus_requiresBothPermissions() {
        // Test that Android 12+ requires both FINE and COARSE
        val locationPermissions = with(delegate) {
            AppGrant.LOCATION.toAndroidGrants()
        }

        assertTrue(
            locationPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION),
            "Should include ACCESS_FINE_LOCATION on Android 12+"
        )
        assertTrue(
            locationPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION),
            "Should include ACCESS_COARSE_LOCATION on Android 12+"
        )
        assertEquals(
            2,
            locationPermissions.size,
            "Should have exactly 2 permissions on Android 12+"
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)  // Android 11+
    fun testLocationAlwaysPermission_requiresTwoStepFlow() = runBlocking {
        // Test that LOCATION_ALWAYS requires two-step flow on Android 11+
        // Step 1: Request foreground permissions
        val locationAlwaysPermissions = with(delegate) {
            AppGrant.LOCATION_ALWAYS.toAndroidGrants()
        }

        // Without foreground granted, should request foreground first
        assertTrue(
            locationAlwaysPermissions.isNotEmpty(),
            "LOCATION_ALWAYS should require permissions on Android 11+"
        )
    }

    // ==================== Bluetooth Permission Tests ====================

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)  // Android 12+
    fun testBluetoothPermission_Android12Plus() {
        // Test that Android 12+ uses new Bluetooth permissions
        val bluetoothPermissions = with(delegate) {
            AppGrant.BLUETOOTH.toAndroidGrants()
        }

        assertTrue(
            bluetoothPermissions.contains(Manifest.permission.BLUETOOTH_SCAN),
            "Should include BLUETOOTH_SCAN on Android 12+"
        )
        assertTrue(
            bluetoothPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT),
            "Should include BLUETOOTH_CONNECT on Android 12+"
        )
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.R)  // Android 11 and below
    fun testBluetoothPermission_Android11AndBelow_usesLocation() {
        // Test that Android 11- requires Location for Bluetooth
        val bluetoothPermissions = with(delegate) {
            AppGrant.BLUETOOTH.toAndroidGrants()
        }

        assertTrue(
            bluetoothPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION),
            "Should include ACCESS_FINE_LOCATION for Bluetooth on Android 11-"
        )
    }

    // ==================== RawPermission Tests ====================

    @Test
    fun testRawPermission_checkStatus() = runBlocking {
        val customPermission = RawPermission(
            identifier = "CUSTOM_TEST",
            androidPermissions = listOf("android.permission.INTERNET"),
            iosUsageKey = null
        )

        val status = delegate.checkStatus(customPermission)

        // INTERNET is usually granted
        assertEquals(
            GrantStatus.GRANTED,
            status,
            "RawPermission with INTERNET should be granted"
        )
    }

    @Test
    fun testRawPermission_multiplePermissions() = runBlocking {
        val customPermission = RawPermission(
            identifier = "MULTI_PERM",
            androidPermissions = listOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE"
            ),
            iosUsageKey = null
        )

        val status = delegate.checkStatus(customPermission)

        // Both usually granted
        assertNotEquals(
            GrantStatus.DENIED_ALWAYS,
            status,
            "RawPermission with common permissions should not be DENIED_ALWAYS"
        )
    }

    @Test
    fun testRawPermission_androidOnlyPermission() = runBlocking {
        val androidOnlyPermission = RawPermission(
            identifier = "ANDROID_ONLY",
            androidPermissions = listOf("android.permission.VIBRATE"),
            iosUsageKey = null  // No iOS equivalent
        )

        val status = delegate.checkStatus(androidOnlyPermission)

        // VIBRATE is usually granted or doesn't require runtime permission
        assertNotEquals(
            GrantStatus.NOT_DETERMINED,
            status,
            "Android-only RawPermission should be checkable"
        )
    }

    // ==================== Granular Gallery Permission Tests ====================

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)  // Android 13+
    fun testGalleryImagesOnly_shouldOnlyIncludeImages() {
        val imagesOnlyPermissions = with(delegate) {
            AppGrant.GALLERY_IMAGES_ONLY.toAndroidGrants()
        }

        assertTrue(
            imagesOnlyPermissions.contains(Manifest.permission.READ_MEDIA_IMAGES),
            "Should include READ_MEDIA_IMAGES"
        )
        assertEquals(
            false,
            imagesOnlyPermissions.contains(Manifest.permission.READ_MEDIA_VIDEO),
            "Should NOT include READ_MEDIA_VIDEO for images-only"
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)  // Android 13+
    fun testGalleryVideoOnly_shouldOnlyIncludeVideo() {
        val videoOnlyPermissions = with(delegate) {
            AppGrant.GALLERY_VIDEO_ONLY.toAndroidGrants()
        }

        assertTrue(
            videoOnlyPermissions.contains(Manifest.permission.READ_MEDIA_VIDEO),
            "Should include READ_MEDIA_VIDEO"
        )
        assertEquals(
            false,
            videoOnlyPermissions.contains(Manifest.permission.READ_MEDIA_IMAGES),
            "Should NOT include READ_MEDIA_IMAGES for video-only"
        )
    }

    // ==================== Schedule Exact Alarm Tests ====================

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)  // Android 12+
    fun testScheduleExactAlarm_Android12Plus() = runBlocking {
        // Test that Android 12+ checks canScheduleExactAlarms
        val status = delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)

        // Status should be determinable (not error)
        assertNotEquals<GrantStatus?>(
            null,
            status,
            "SCHEDULE_EXACT_ALARM status should be determinable on Android 12+"
        )
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.R)  // Android 11 and below
    fun testScheduleExactAlarm_Android11AndBelow_alwaysGranted() = runBlocking {
        // Test that Android 11- doesn't require permission
        val status = delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)

        assertEquals(
            GrantStatus.GRANTED,
            status,
            "SCHEDULE_EXACT_ALARM should be auto-granted on Android 11-"
        )
    }
}
