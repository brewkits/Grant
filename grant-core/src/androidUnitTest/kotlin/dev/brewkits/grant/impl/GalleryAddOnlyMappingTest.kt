package dev.brewkits.grant.impl

import android.Manifest
import android.os.Build
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [AppGrant.GALLERY_ADD_ONLY] — save-only media access.
 *
 * Under scoped storage (API 29+) an app inserts into its own `MediaStore` collections with no
 * permission at all, so the correct behaviour is to report GRANTED without ever prompting.
 * Only API 26-28 needs `WRITE_EXTERNAL_STORAGE`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GalleryAddOnlyMappingTest {

    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        delegate = PlatformGrantDelegate(RuntimeEnvironment.getApplication(), InMemoryGrantStore())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun `maps to WRITE_EXTERNAL_STORAGE on API 26`() {
        val permissions = with(delegate) { AppGrant.GALLERY_ADD_ONLY.toAndroidGrants() }
        assertEquals(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `maps to WRITE_EXTERNAL_STORAGE on API 28`() {
        val permissions = with(delegate) { AppGrant.GALLERY_ADD_ONLY.toAndroidGrants() }
        assertEquals(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `maps to nothing from API 29 onwards - scoped storage needs no permission`() {
        val permissions = with(delegate) { AppGrant.GALLERY_ADD_ONLY.toAndroidGrants() }
        assertEquals(emptyList<String>(), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `maps to nothing on API 34`() {
        val permissions = with(delegate) { AppGrant.GALLERY_ADD_ONLY.toAndroidGrants() }
        assertEquals(emptyList<String>(), permissions)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `reports GRANTED without a prompt under scoped storage`() = runTest {
        // The whole point of the grant: a save-only feature must not ask the user for anything
        // on a modern device. An empty mapping resolves to GRANTED in checkStatus.
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.GALLERY_ADD_ONLY))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `is not confused with read access - GALLERY still maps to read permissions`() {
        val addOnly = with(delegate) { AppGrant.GALLERY_ADD_ONLY.toAndroidGrants() }
        val readWrite = with(delegate) { AppGrant.GALLERY.toAndroidGrants() }
        assertEquals(emptyList<String>(), addOnly)
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            ),
            readWrite,
        )
    }
}
