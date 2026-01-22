package dev.brewkits.grant

/**
 * Unified grant status across platforms.
 *
 * This enum standardizes grant states for both iOS and Android,
 * making it easy for UI layer to handle all cases consistently.
 */
enum class GrantStatus {
    /**
     * Grant has been granted by the user.
     * The feature can be used immediately.
     */
    GRANTED,

    /**
     * Grant was denied by the user, but can be requested again.
     *
     * **Android**: User denied but didn't check "Don't ask again"
     * **iOS**: User denied the first time (can show alert to explain)
     *
     * **Next Action**: Show rationale dialog, then request again
     */
    DENIED,

    /**
     * Grant was permanently denied.
     * The system will not show the grant dialog anymore.
     *
     * **Android**: User checked "Don't ask again" and denied
     * **iOS**: User denied multiple times
     *
     * **Next Action**: Must open app settings for user to manually enable
     */
    DENIED_ALWAYS,

    /**
     * Grant has never been requested before.
     * This is the initial state.
     *
     * **Next Action**: Request grant (system dialog will appear)
     */
    NOT_DETERMINED
}
