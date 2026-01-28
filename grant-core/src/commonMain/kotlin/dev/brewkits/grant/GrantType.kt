package dev.brewkits.grant

/**
 * Defines all grants used in the application.
 *
 * This enum provides a clean, platform-agnostic abstraction over
 * Android and iOS grant systems.
 *
 * **Extensibility:**
 * - For common permissions, use AppGrant enum values
 * - For custom permissions, use RawPermission (see GrantPermission interface)
 * - AppGrant implements GrantPermission for unified API
 *
 * **To add new grants to the library:**
 * 1. Add new enum entry here
 * 2. Update platform-specific implementations in PlatformGrantDelegate
 * 3. Add required Info.plist keys (iOS) or AndroidManifest grants (Android)
 *
 * **For users who need custom permissions:**
 * ```kotlin
 * val customPermission = RawPermission(
 *     identifier = "MY_CUSTOM_PERMISSION",
 *     androidPermissions = listOf("android.permission.CUSTOM"),
 *     iosUsageKey = "NSCustomUsageDescription"
 * )
 * val status = grantManager.request(customPermission)
 * ```
 */
enum class AppGrant : GrantPermission {
    /**
     * Camera grant for photo/video capture
     * - Android: CAMERA
     * - iOS: NSCameraUsageDescription
     */
    CAMERA,

    /**
     * Photo library/gallery access (both images and videos)
     * - Android: READ_MEDIA_IMAGES + READ_MEDIA_VIDEO (API 33+) or READ_EXTERNAL_STORAGE
     * - iOS: NSPhotoLibraryUsageDescription
     *
     * ⚠️ Important: If you only need images OR videos (not both), use GALLERY_IMAGES_ONLY
     * or GALLERY_VIDEO_ONLY instead. Requesting both permissions when only one is declared
     * in AndroidManifest.xml will result in silent denial on Android 13+.
     */
    GALLERY,

    /**
     * Photo library access (images only)
     * - Android: READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE
     * - iOS: NSPhotoLibraryUsageDescription (same as GALLERY - iOS doesn't distinguish)
     *
     * Use this if your app only needs image access (not videos).
     * This prevents silent denial when READ_MEDIA_VIDEO is not declared in AndroidManifest.xml.
     */
    GALLERY_IMAGES_ONLY,

    /**
     * Photo library access (videos only)
     * - Android: READ_MEDIA_VIDEO (API 33+) or READ_EXTERNAL_STORAGE
     * - iOS: NSPhotoLibraryUsageDescription (same as GALLERY - iOS doesn't distinguish)
     *
     * Use this if your app only needs video access (not images).
     * This prevents silent denial when READ_MEDIA_IMAGES is not declared in AndroidManifest.xml.
     */
    GALLERY_VIDEO_ONLY,

    /**
     * Storage access
     * - Android: READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE
     * - iOS: Not applicable (sandbox)
     */
    STORAGE,

    /**
     * Location grant (when in use)
     * - Android: ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION
     * - iOS: NSLocationWhenInUseUsageDescription
     */
    LOCATION,

    /**
     * Location grant (always/background)
     * - Android: ACCESS_BACKGROUND_LOCATION
     * - iOS: NSLocationAlwaysUsageDescription
     */
    LOCATION_ALWAYS,

    /**
     * Push notification grant
     * - Android: POST_NOTIFICATIONS (API 33+)
     * - iOS: User Notification authorization
     */
    NOTIFICATION,

    /**
     * Schedule exact alarms (Android 12+)
     * - Android API 31-32: SCHEDULE_EXACT_ALARM (user-granted permission)
     * - Android API 33+: USE_EXACT_ALARM (install-time permission, no dialog)
     * - iOS: Not applicable (always allowed)
     *
     * Use case: Alarm clock apps, calendar reminders, medication reminders.
     * Note: Android 13+ apps that legitimately need exact alarms can use USE_EXACT_ALARM
     * which doesn't require user approval. SCHEDULE_EXACT_ALARM requires user to manually
     * grant in Settings.
     */
    SCHEDULE_EXACT_ALARM,

    /**
     * Bluetooth grant
     * - Android API 31+: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE
     * - Android API 29-30: ACCESS_FINE_LOCATION (required for BLE scanning)
     * - Android API <29: ACCESS_COARSE_LOCATION
     * - iOS: NSBluetoothAlwaysUsageDescription (CoreBluetooth)
     *
     * Note: On Android < 31, location grant is required for Bluetooth scanning.
     * The implementation handles API version differences automatically.
     */
    BLUETOOTH,

    /**
     * Microphone/audio recording
     * - Android: RECORD_AUDIO
     * - iOS: NSMicrophoneUsageDescription
     */
    MICROPHONE,

    /**
     * Contacts access
     * - Android: READ_CONTACTS
     * - iOS: NSContactsUsageDescription
     */
    CONTACTS,

    /**
     * Motion / Activity Recognition
     * - Android: ACTIVITY_RECOGNITION (API 29+), BODY_SENSORS
     * - iOS: CMMotionActivityManager (Motion Usage)
     */
    MOTION,

    /**
     * Calendar access
     * - Android: READ_CALENDAR / WRITE_CALENDAR
     * - iOS: NSCalendarsUsageDescription (EventKit)
     */
    CALENDAR,
    ;

    /**
     * Unique identifier for this permission.
     * Uses the enum constant name (e.g., "CAMERA", "LOCATION").
     */
    override val identifier: String get() = name
}
