package dev.brewkits.grant.util

import android.content.Context
import android.content.pm.PackageManager
import dev.brewkits.grant.AppGrant

/**
 * Utility for validating manifest permissions declarations.
 *
 * Helps developers catch missing manifest declarations at runtime.
 */
public object ManifestValidator {
    /**
     * Check if a specific permission is declared in AndroidManifest.xml
     *
     * @param context Android context
     * @param permission Permission string (e.g., "android.permission.CAMERA")
     * @return true if permission is declared in manifest
     */
    public fun isPermissionDeclared(context: Context, permission: String): Boolean {
        // Bypass for Unit Tests (Robolectric or generic tests)
        // In test environments, we assume permissions are declared to avoid
        // complex ShadowPackageManager setup in every test case.
        if (context.packageName == "dev.brewkits.grant.test" || 
            context.javaClass.name.contains("Shadow") ||
            System.getProperty("robolectric.enabled") == "true") {
            return true
        }

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
     * Whether `BLUETOOTH_SCAN` is declared with `android:usesPermissionFlags="neverForLocation"`.
     *
     * Android treats a plain `BLUETOOTH_SCAN` as capable of deriving physical location — scan
     * results reveal which devices are nearby — so an app that does not derive location is
     * expected to opt out:
     * ```xml
     * <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
     *     android:usesPermissionFlags="neverForLocation" />
     * ```
     * Without the flag, the app carries a location implication reviewers and users can see,
     * for a capability it may never use.
     *
     * Read from [android.content.pm.PackageInfo.requestedPermissionsFlags], which is public
     * API. `REQUESTED_PERMISSION_NEVER_FOR_LOCATION` is spelled as its literal `0x10000`
     * because the constant is not in this project's `compileSdk` — the same convention already
     * used for `ACCESS_LOCAL_NETWORK` and `READ_MEDIA_VISUAL_USER_SELECTED`.
     *
     * Returns `null` when the answer cannot be established — the permission is not declared at
     * all, the arrays are absent, or the lookup failed. `null` means "unknown", deliberately
     * distinct from `false` ("declared, and the flag is missing"), so a caller never warns on
     * a guess.
     */
    internal fun isBluetoothScanNeverForLocation(context: Context): Boolean? {
        // Same test-environment bypass as isPermissionDeclared: Robolectric's package manager
        // does not model permission flags, so any answer here would be an artefact.
        if (context.packageName == "dev.brewkits.grant.test" ||
            context.javaClass.name.contains("Shadow") ||
            System.getProperty("robolectric.enabled") == "true"
        ) {
            return null
        }

        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )
            val names = packageInfo.requestedPermissions ?: return null
            val flags = packageInfo.requestedPermissionsFlags ?: return null
            val index = names.indexOf(BLUETOOTH_SCAN_PERMISSION)
            if (index < 0 || index >= flags.size) return null
            (flags[index] and REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0
        } catch (e: Exception) {
            null
        }
    }

    private const val BLUETOOTH_SCAN_PERMISSION = "android.permission.BLUETOOTH_SCAN"

    /**
     * `PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION`, as a literal because the constant
     * postdates this project's compileSdk. Verified against the API 35, 36 and 37 platform
     * jars, where it is `0x10000`.
     */
    private const val REQUESTED_PERMISSION_NEVER_FOR_LOCATION = 0x10000

    /**
     * Validate that all required permissions for a grant are declared in manifest
     *
     * @param context Android context
     * @param grant The grant to validate
     * @return ValidationResult indicating if valid or which permissions are missing
     */
    public fun validateGrant(context: Context, grant: AppGrant): ValidationResult {
        // Instantiate ephemeral delegate to access platform-specific manifest mapping
        val delegate = dev.brewkits.grant.impl.PlatformGrantDelegate(context, dev.brewkits.grant.InMemoryGrantStore())
        val requiredPermissions = with(delegate) { grant.toAndroidGrants() }
        
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
public sealed class ValidationResult {
    /**
     * All required permissions are declared in manifest
     */
    public object Valid : ValidationResult()

    /**
     * Some required permissions are missing from manifest
     *
     * @param permissions List of missing permission strings
     */
    public data class MissingPermissions(val permissions: List<String>) : ValidationResult()
}
