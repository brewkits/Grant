package dev.brewkits.grant

/** Configuration specific to each platform's behavior. */
internal expect object PlatformConfig {
    /** Whether the OS supports a "soft denial" state where a rationale can be shown and the user can be asked again natively. */
    val isRationaleSupported: Boolean
}
