package dev.brewkits.grant

/**
 * The unified state of a system permission across Android and iOS.
 *
 * This enum standardizes platform-specific authorization states, enabling the
 * UI layer to handle all scenarios consistently.
 */
enum class GrantStatus {
    /**
     * The permission has been granted by the user.
     *
     * The associated feature can be used immediately.
     */
    GRANTED,

    /**
     * The permission is granted with limited scope.
     *
     * - **Android 14+**: User selected "Select photos and videos" (Media Visual Picker).
     * - **iOS**: User selected "Limited" access to the Photo Library.
     *
     * **Developer Action**: Proceed with limited access or inform the user why
     * full access would provide a better experience.
     */
    PARTIAL_GRANTED,

    /**
     * The permission was denied, but can be requested again natively.
     *
     * - **Android**: User clicked "Deny" but did not check "Don't ask again".
     * - **iOS**: Internal state indicating the user has denied, but a rationale
     *   can still be shown before directing them to Settings.
     *
     * **Developer Action**: Show a rationale dialog to explain why the permission
     * is needed, then trigger a new request.
     */
    DENIED,

    /**
     * The permission was permanently denied and cannot be requested via system dialog.
     *
     * - **Android**: User checked "Don't ask again" or denied multiple times.
     * - **iOS**: User clicked "Don't Allow" on the system dialog.
     *
     * **Developer Action**: Direct the user to the app's system settings page
     * using `grantManager.openSettings()`.
     */
    DENIED_ALWAYS,

    /**
     * The permission has never been requested in the current session/install.
     *
     * This is the initial state before any system dialog is shown.
     *
     * **Developer Action**: Trigger a request to show the system permission dialog.
     */
    NOT_DETERMINED
}
