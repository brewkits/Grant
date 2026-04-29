package dev.brewkits.grant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dev.brewkits.grant.AppGrant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ManifestValidatorTest {

    private lateinit var context: Context

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `isPermissionDeclared returns true when declared`() {
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val packageInfo = shadowPackageManager.getInternalMutablePackageInfo(context.packageName)
        packageInfo.requestedPermissions = arrayOf(Manifest.permission.CAMERA)

        assertTrue(ManifestValidator.isPermissionDeclared(context, Manifest.permission.CAMERA))
    }

    @Test
    fun `isPermissionDeclared returns false when not declared`() {
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val packageInfo = shadowPackageManager.getInternalMutablePackageInfo(context.packageName)
        packageInfo.requestedPermissions = emptyArray()

        assertEquals(false, ManifestValidator.isPermissionDeclared(context, Manifest.permission.CAMERA))
    }

    @Test
    fun `validateGrant returns Valid when all declared`() {
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val packageInfo = shadowPackageManager.getInternalMutablePackageInfo(context.packageName)
        packageInfo.requestedPermissions = arrayOf(Manifest.permission.CAMERA)

        val result = ManifestValidator.validateGrant(context, AppGrant.CAMERA)
        assertIs<ValidationResult.Valid>(result)
    }

    @Test
    fun `validateGrant returns MissingPermissions when not declared`() {
        val shadowPackageManager = Shadows.shadowOf(context.packageManager)
        val packageInfo = shadowPackageManager.getInternalMutablePackageInfo(context.packageName)
        packageInfo.requestedPermissions = emptyArray()

        val result = ManifestValidator.validateGrant(context, AppGrant.CAMERA)
        assertIs<ValidationResult.MissingPermissions>(result)
        assertEquals(listOf(Manifest.permission.CAMERA), (result as ValidationResult.MissingPermissions).permissions)
    }
}
