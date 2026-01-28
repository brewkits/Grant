package dev.brewkits.grant.util

import android.content.Context
import android.content.pm.PackageManager
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.impl.toAndroidGrants

/**
 * Utility for validating manifest permissions declarations.
 *
 * Helps developers catch missing manifest declarations at runtime.
 */
object ManifestValidator {
    /**
     * Check if a specific permission is declared in AndroidManifest.xml
     *
     * @param context Android context
     * @param permission Permission string (e.g., "android.permission.CAMERA")
     * @return true if permission is declared in manifest
     */
    fun isPermissionDeclared(context: Context, permission: String): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate that all required permissions for a grant are declared in manifest
     *
     * @param context Android context
     * @param grant The grant to validate
     * @return ValidationResult indicating if valid or which permissions are missing
     */
    fun validateGrant(context: Context, grant: AppGrant): ValidationResult {
        val requiredPermissions = dev.brewkits.grant.impl.toAndroidGrants(grant, context)
        val missingPermissions = requiredPermissions.filter {
            !isPermissionDeclared(context, it)
        }

        return if (missingPermissions.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.MissingPermissions(missingPermissions)
        }
    }
}

/**
 * Result of manifest validation check
 */
sealed class ValidationResult {
    /**
     * All required permissions are declared in manifest
     */
    object Valid : ValidationResult()

    /**
     * Some required permissions are missing from manifest
     *
     * @param permissions List of missing permission strings
     */
    data class MissingPermissions(val permissions: List<String>) : ValidationResult()
}
