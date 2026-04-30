package dev.brewkits.grant

/** 
 * Internal configuration providing platform-specific capability flags. 
 */
internal expect object PlatformConfig {
    /** 
     * Whether the platform supports a native "soft denial" state where a 
     * rationale can be shown to the user.
     * 
     * - **Android**: Returns `true` (via `shouldShowRequestPermissionRationale`).
     * - **iOS**: Returns `false` (iOS does not have a native rationale state).
     */
    val isRationaleSupported: Boolean
}
