package dev.brewkits.grant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the real behaviour of `isPermissionDeclared` on both the API 33+ and the legacy
 * `getPackageInfo` paths.
 *
 * It used to open with a test-only bypass in production code (a `dev.brewkits.grant.test`
 * package match, a "Shadow" class-name match, and a `robolectric.enabled` system property).
 * Measured: that bypass never fired under these Robolectric runs (package `org.robolectric.default`),
 * so these tests pass against the old code too — they guard the removal, they do not prove a fix.
 * It did fire for the library's own instrumented test APK, whose package is `dev.brewkits.grant.test`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ManifestValidatorDeclaredTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun declare(vararg permissions: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = context.packageName
                requestedPermissions = arrayOf(*permissions)
            },
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `declared permission is reported declared on API 33 and above`() {
        declare(Manifest.permission.CAMERA)
        assertTrue(ManifestValidator.isPermissionDeclared(context, Manifest.permission.CAMERA))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `undeclared permission is reported undeclared on API 33 and above`() {
        declare(Manifest.permission.CAMERA)
        assertFalse(ManifestValidator.isPermissionDeclared(context, Manifest.permission.RECORD_AUDIO))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `declared permission is reported declared below API 33`() {
        declare(Manifest.permission.CAMERA)
        assertTrue(ManifestValidator.isPermissionDeclared(context, Manifest.permission.CAMERA))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `undeclared permission is reported undeclared below API 33`() {
        declare(Manifest.permission.CAMERA)
        assertFalse(ManifestValidator.isPermissionDeclared(context, Manifest.permission.RECORD_AUDIO))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `a package that declares nothing reports everything undeclared`() {
        declare()
        assertFalse(ManifestValidator.isPermissionDeclared(context, Manifest.permission.CAMERA))
    }
}
