package dev.brewkits.grant

/**
 * Represents a permission that can be requested from the system.
 *
 * **Why This Exists:**
 * - AppGrant enum covers common permissions (Camera, Location, etc.)
 * - But new OS permissions are added constantly (Android 15, iOS 18, etc.)
 * - Users may need custom or proprietary permissions
 * - Enum cannot be extended by library users
 *
 * **Solution:**
 * - Sealed interface allows both enum (common case) and custom permissions
 * - AppGrant implements this interface (backward compatible)
 * - RawPermission allows custom permissions (extensibility)
 *
 * **Use Cases:**
 * ```kotlin
 * // Standard permission (recommended)
 * val status = grantManager.request(AppGrant.CAMERA)
 *
 * // Custom Android 15 permission (when library doesn't support it yet)
 * val customPermission = RawPermission(
 *     identifier = "ACCESS_AI_PROCESSOR",
 *     androidPermissions = listOf("android.permission.ACCESS_AI_PROCESSOR"),
 *     iosUsageKey = null
 * )
 * val status = grantManager.request(customPermission)
 *
 * // Enterprise custom permission
 * val enterprisePermission = RawPermission(
 *     identifier = "COMPANY_INTERNAL_API",
 *     androidPermissions = listOf("com.company.permission.INTERNAL_API"),
 *     iosUsageKey = "NSCompanyInternalAPIUsageDescription"
 * )
 * ```
 *
 * **Benefits:**
 * - ✅ Backward compatible: Existing code unchanged
 * - ✅ Extensible: Users can add custom permissions
 * - ✅ Type-safe: Compiler enforces GrantPermission usage
 * - ✅ No library update needed for new OS permissions
 */
sealed interface GrantPermission {
    /**
     * Unique identifier for this permission.
     * Used for logging, debugging, and display purposes.
     */
    val identifier: String
}

/**
 * Custom permission defined by raw platform strings.
 *
 * **When to Use:**
 * - New Android/iOS permission not yet in AppGrant enum
 * - Custom enterprise or proprietary permissions
 * - Testing or prototyping with experimental permissions
 * - Platform-specific permissions that don't fit AppGrant categories
 *
 * **Example: Android 15 New Permission**
 * ```kotlin
 * val bodyTemperature = RawPermission(
 *     identifier = "BODY_TEMPERATURE",
 *     androidPermissions = listOf("android.permission.BODY_TEMPERATURE"),
 *     iosUsageKey = null  // Android-only permission
 * )
 * ```
 *
 * **Example: iOS-only Permission**
 * ```kotlin
 * val healthKit = RawPermission(
 *     identifier = "HEALTH_KIT",
 *     androidPermissions = emptyList(),  // iOS-only
 *     iosUsageKey = "NSHealthShareUsageDescription"
 * )
 * ```
 *
 * **Example: Cross-platform Custom Permission**
 * ```kotlin
 * val biometric = RawPermission(
 *     identifier = "BIOMETRIC",
 *     androidPermissions = listOf("android.permission.USE_BIOMETRIC"),
 *     iosUsageKey = "NSFaceIDUsageDescription"
 * )
 * ```
 *
 * **Important Notes:**
 * - You are responsible for platform compatibility checks
 * - Invalid permissions will be denied by the OS
 * - Info.plist keys are still required for iOS
 *
 * @param identifier Human-readable identifier for debugging/logging
 * @param androidPermissions List of Android Manifest.permission strings
 * @param iosUsageKey iOS Info.plist usage description key (e.g., "NSCameraUsageDescription")
 */
data class RawPermission(
    override val identifier: String,
    val androidPermissions: List<String>,
    val iosUsageKey: String? = null
) : GrantPermission

// Note: AppGrant enum is updated separately to implement GrantPermission
// This is done in GrantType.kt to maintain backward compatibility
