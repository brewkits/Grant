package dev.brewkits.grant

/**
 * Core interface for grant management.
 *
 * This is the ONLY interface that ViewModels and feature modules should depend on.
 * Implementation details are hidden behind this abstraction.
 *
 * **Design Pattern**: Wrapper/Adapter Pattern
 * **Benefits**:
 * - Easy to swap underlying implementation
 * - No coupling between business logic and platform-specific code
 * - Simplified testing (mock this interface)
 *
 * @see MyGrantManager for the default implementation
 * @see GrantHandler for ViewModel usage patterns
 */
interface GrantManager {
    /**
     * Checks the current status of a permission WITHOUT showing any UI.
     *
     * Use this to check before triggering features or to update UI state.
     *
     * **Supports both built-in and custom permissions:**
     * - AppGrant enum values (recommended for common permissions)
     * - RawPermission for custom/platform-specific permissions
     *
     * @param grant The permission to check (AppGrant or RawPermission)
     * @return Current status (GRANTED, DENIED, DENIED_ALWAYS, NOT_DETERMINED)
     *
     * **Example (built-in permission)**:
     * ```kotlin
     * val status = grantManager.checkStatus(AppGrant.CAMERA)
     * if (status == GrantStatus.GRANTED) {
     *     openCamera()
     * }
     * ```
     *
     * **Example (custom permission)**:
     * ```kotlin
     * val customPermission = RawPermission(
     *     identifier = "CUSTOM_FEATURE",
     *     androidPermissions = listOf("com.company.permission.CUSTOM"),
     *     iosUsageKey = "NSCustomUsageDescription"
     * )
     * val status = grantManager.checkStatus(customPermission)
     * ```
     */
    suspend fun checkStatus(grant: GrantPermission): GrantStatus

    /**
     * Requests a permission from the user.
     *
     * This function will:
     * - Show system permission dialog (if first time or previously denied softly)
     * - Return immediately if already granted
     * - Suspend until user makes a choice
     *
     * **Supports both built-in and custom permissions:**
     * - AppGrant enum values (recommended for common permissions)
     * - RawPermission for custom/platform-specific permissions
     *
     * @param grant The permission to request (AppGrant or RawPermission)
     * @return Status after the request (GRANTED if approved, DENIED/DENIED_ALWAYS if rejected)
     *
     * **Example (built-in permission)**:
     * ```kotlin
     * val result = grantManager.request(AppGrant.CAMERA)
     * when (result) {
     *     GrantStatus.GRANTED -> openCamera()
     *     GrantStatus.DENIED -> showRationaleDialog()
     *     GrantStatus.DENIED_ALWAYS -> showGoToSettingsDialog()
     *     else -> {}
     * }
     * ```
     *
     * **Example (custom permission)**:
     * ```kotlin
     * val customPermission = RawPermission(
     *     identifier = "CUSTOM_FEATURE",
     *     androidPermissions = listOf("com.company.permission.CUSTOM"),
     *     iosUsageKey = "NSCustomUsageDescription"
     * )
     * val result = grantManager.request(customPermission)
     * ```
     */
    suspend fun request(grant: GrantPermission): GrantStatus

    /**
     * Opens the app's settings page where user can manually enable grants.
     *
     * Use this when grant is DENIED_ALWAYS and must be enabled via Settings.
     *
     * **Platform Behavior**:
     * - Android: Opens App Info > Grants
     * - iOS: Opens Settings > [Your App]
     *
     * **Example**:
     * ```kotlin
     * // In UI when showing "Go to Settings" dialog
     * Button("Open Settings") {
     *     grantManager.openSettings()
     * }
     * ```
     */
    fun openSettings()
}
