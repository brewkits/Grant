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
     * Checks the current status of a grant WITHOUT showing any UI.
     *
     * Use this to check before triggering features or to update UI state.
     *
     * @param grant The grant to check
     * @return Current status (GRANTED, DENIED, DENIED_ALWAYS, NOT_DETERMINED)
     *
     * **Example**:
     * ```kotlin
     * val status = grantManager.checkStatus(AppGrant.CAMERA)
     * if (status == GrantStatus.GRANTED) {
     *     openCamera()
     * }
     * ```
     */
    suspend fun checkStatus(grant: AppGrant): GrantStatus

    /**
     * Requests a grant from the user.
     *
     * This function will:
     * - Show system grant dialog (if first time or previously denied softly)
     * - Return immediately if already granted
     * - Suspend until user makes a choice
     *
     * @param grant The grant to request
     * @return Status after the request (GRANTED if approved, DENIED/DENIED_ALWAYS if rejected)
     *
     * **Example**:
     * ```kotlin
     * val result = grantManager.request(AppGrant.CAMERA)
     * when (result) {
     *     GrantStatus.GRANTED -> openCamera()
     *     GrantStatus.DENIED -> showRationaleDialog()
     *     GrantStatus.DENIED_ALWAYS -> showGoToSettingsDialog()
     *     else -> {}
     * }
     * ```
     */
    suspend fun request(grant: AppGrant): GrantStatus

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
