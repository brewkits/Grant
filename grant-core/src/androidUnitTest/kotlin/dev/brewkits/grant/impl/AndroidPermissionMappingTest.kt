package dev.brewkits.grant.impl

import android.Manifest
import android.os.Build
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.fakes.FakeGrantManager
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Android-specific permission mapping logic.
 *
 * Verifies that AppGrant types translate to the correct Android Manifest permissions
 * across different SDK versions (API 21-35).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidPermissionMappingTest {

    private lateinit var delegate: PlatformGrantDelegate
    
    @Before
    fun setup() {
        // We need a context, Robolectric provides a shadow one
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `STORAGE maps to legacy READ_EXTERNAL_STORAGE on API 21`() {
        val permissions = with(delegate) { AppGrant.STORAGE.toAndroidGrants() }
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `STORAGE maps to granular media permissions on API 33`() {
        val permissions = with(delegate) { AppGrant.STORAGE.toAndroidGrants() }
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
            permissions
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `STORAGE maps to visual user selected on API 34`() {
        val permissions = with(delegate) { AppGrant.STORAGE.toAndroidGrants() }
        assertTrue(permissions.contains("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `NOTIFICATION returns empty list on API 21`() {
        val permissions = with(delegate) { AppGrant.NOTIFICATION.toAndroidGrants() }
        assertEquals(emptyList(), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `NOTIFICATION maps to POST_NOTIFICATIONS on API 33`() {
        val permissions = with(delegate) { AppGrant.NOTIFICATION.toAndroidGrants() }
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    fun `BLUETOOTH maps to LOCATION on API 21`() {
        val permissions = with(delegate) { AppGrant.BLUETOOTH.toAndroidGrants() }
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `BLUETOOTH maps to SCAN and CONNECT on API 31`() {
        val permissions = with(delegate) { AppGrant.BLUETOOTH.toAndroidGrants() }
        assertEquals(
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            permissions
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `LOCATION maps to FINE on API 23`() {
        val permissions = with(delegate) { AppGrant.LOCATION.toAndroidGrants() }
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `LOCATION maps to FINE and COARSE on API 31`() {
        val permissions = with(delegate) { AppGrant.LOCATION.toAndroidGrants() }
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            permissions
        )
    }
}
