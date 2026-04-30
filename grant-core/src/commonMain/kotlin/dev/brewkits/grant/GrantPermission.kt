package dev.brewkits.grant

/**
 * Represents a system permission that can be requested on Android and iOS.
 *
 * This sealed interface provides a unified, type-safe abstraction over common OS
 * permissions ([AppGrant]) and custom platform-specific permissions ([RawPermission]).
 *
 * ### Extensibility
 * While [AppGrant] covers 90% of common use cases, new OS versions (Android 15+, iOS 18+)
 * and enterprise-specific requirements often demand permissions not yet included in
 * the library. [RawPermission] allows you to support these instantly without
 * waiting for a library update.
 *
 * ### Example Usage
 * ```kotlin
 * // Standard permission (recommended)
 * val status = grantManager.request(AppGrant.CAMERA)
 *
 * // Custom Android 15 permission
 * val customPermission = RawPermission(
 *     identifier = "ACCESS_AI_PROCESSOR",
 *     androidPermissions = listOf("android.permission.ACCESS_AI_PROCESSOR")
 * )
 * val status = grantManager.request(customPermission)
 * ```
 */
sealed interface GrantPermission {
    /**
     * Unique identifier for this permission.
     *
     * Used internally for caching, logging, and as a key in [GrantStore].
     * For [AppGrant], this is the enum name. For [RawPermission], this is
     * the user-defined string.
     */
    val identifier: String
}

/**
 * A custom permission defined by raw platform strings.
 *
 * Use this to support permissions that are not yet part of the [AppGrant] enum,
 * such as experimental OS features or proprietary enterprise permissions.
 *
 * ### Cross-Platform Mapping
 * You are responsible for ensuring that the [androidPermissions] and [iosUsageKey]
 * logically represent the same feature for the user.
 *
 * @property identifier Human-readable identifier used for caching and logs.
 * @property androidPermissions List of manifest permission strings (e.g., `Manifest.permission.CAMERA`).
 * @property iosUsageKey The `Info.plist` key string (e.g., `NSCameraUsageDescription`).
 *                       If null, this permission will be treated as always granted on iOS
 *                       unless [identifier] was previously marked as requested in [GrantStore].
 */
data class RawPermission(
    override val identifier: String,
    val androidPermissions: List<String>,
    val iosUsageKey: String? = null
) : GrantPermission
