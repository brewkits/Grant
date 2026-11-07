package dev.brewkits.grant.desktop

/**
 * Windows camera/microphone device-class access for the current process.
 * This is not hardware presence, a successful capture session, or a permission request.
 * Applications must still handle access errors from their media API.
 */
public enum class WindowsPrivacyStatus {
    /** Windows reports access allowed; this does not imply a device exists or is idle. */
    ALLOWED,
    /** Windows reports that the user has disallowed access. */
    DENIED_BY_USER,
    /** Windows reports that the system has disallowed access. */
    DENIED_BY_SYSTEM,
    /** Windows returned Unspecified; do not treat this as allowed or as a first request. */
    UNSPECIFIED,
    /** Unsupported permission/host, missing bridge, or a failed/unknown native response. */
    UNAVAILABLE,
}
