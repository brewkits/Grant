package dev.brewkits.grant.demo

/**
 * Platform-specific OS version information for demo/testing display.
 */
expect object OsInfo {
    /** e.g. "Android" or "iOS" */
    val platform: String

    /** e.g. "Android 14" or "iOS 17.2" */
    val osVersion: String

    /** e.g. "API 34" or "arm64" */
    val apiLevel: String

    /**
     * Human-readable description of what behavior to expect for Gallery on this OS:
     * - Android API 21:  "READ_EXTERNAL_STORAGE (legacy)"
     * - Android API 33:  "READ_MEDIA_IMAGES + VIDEO"
     * - Android API 34+: "Granular + Partial access"
     * - iOS any:         "PHPhotoLibrary — PARTIAL = Limited"
     */
    fun galleryBehaviorNote(): String

    /**
     * Human-readable note for Notification permission on this OS.
     */
    fun notificationBehaviorNote(): String

    /**
     * Human-readable note for Motion permission on this OS.
     */
    fun motionBehaviorNote(): String
}
