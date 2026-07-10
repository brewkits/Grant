package dev.brewkits.grant

/**
 * A platform-agnostic abstraction for the most common system permissions.
 *
 * Each entry maps to the appropriate native APIs on Android and iOS, handling
 * version-specific complexities (e.g., Android 13/14 granular media access, 
 * Bluetooth API 31 changes).
 */
enum class AppGrant : GrantPermission {
    /**
     * Access to the camera hardware for photo or video capture.
     *
     * - **Android**: `Manifest.permission.CAMERA`
     * - **iOS**: `NSCameraUsageDescription`
     */
    CAMERA,

    /**
     * Full access to the photo and video library.
     *
     * - **Android**: Maps to `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` (API 33+) or
     *   `READ_EXTERNAL_STORAGE`. Supports Android 14+ "Partial Access" flow.
     * - **iOS**: `NSPhotoLibraryUsageDescription`. Supports iOS "Limited" access.
     *
     * ⚠️ **Android Note**: If your app only requires images or videos, use
     * [GALLERY_IMAGES_ONLY] or [GALLERY_VIDEO_ONLY] to minimize permission scope
     * and avoid silent denial if you haven't declared both in your Manifest.
     */
    GALLERY,

    /**
     * Read-only access to image files in the photo library.
     *
     * - **Android**: `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE`.
     * - **iOS**: `NSPhotoLibraryUsageDescription`.
     */
    GALLERY_IMAGES_ONLY,

    /**
     * Read-only access to video files in the photo library.
     *
     * - **Android**: `READ_MEDIA_VIDEO` (API 33+) or `READ_EXTERNAL_STORAGE`.
     * - **iOS**: `NSPhotoLibraryUsageDescription`.
     */
    GALLERY_VIDEO_ONLY,

    /**
     * Access to shared external storage.
     *
     * - **Android**: `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`.
     * - **iOS**: No-op (always GRANTED due to sandbox architecture).
     */
    STORAGE,

    /**
     * Standard location access when the app is in the foreground.
     *
     * - **Android**: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`.
     * - **iOS**: `NSLocationWhenInUseUsageDescription`.
     */
    LOCATION,

    /**
     * Background location access.
     *
     * - **Android**: `ACCESS_BACKGROUND_LOCATION` (requires special handling on API 30+).
     * - **iOS**: `NSLocationAlwaysUsageDescription`.
     */
    LOCATION_ALWAYS {
        override val requiresBackgroundUpgrade: Boolean get() = true
    },

    /**
     * Permission to show push or local notifications.
     *
     * - **Android**: `POST_NOTIFICATIONS` (API 33+).
     * - **iOS**: `UNUserNotificationCenter` authorization.
     */
    NOTIFICATION,

    /**
     * Permission to schedule exact alarms at a specific time.
     *
     * - **Android**: `SCHEDULE_EXACT_ALARM` (API 31+).
     * - **iOS**: No-op (always GRANTED).
     */
    SCHEDULE_EXACT_ALARM,

    /**
     * Bluetooth scanning, connecting, and central mode operations.
     *
     * - **Android**: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` (API 31+) or 
     *   `ACCESS_FINE_LOCATION` (API < 31).
     * - **iOS**: `NSBluetoothAlwaysUsageDescription`.
     */
    BLUETOOTH,

    /**
     * Bluetooth peripheral mode for advertising the device.
     *
     * - **Android**: `BLUETOOTH_ADVERTISE` (API 31+).
     * - **iOS**: `NSBluetoothAlwaysUsageDescription`.
     */
    BLUETOOTH_ADVERTISE,

    /**
     * Access to the microphone for audio recording.
     *
     * - **Android**: `RECORD_AUDIO`.
     * - **iOS**: `NSMicrophoneUsageDescription`.
     */
    MICROPHONE,

    /**
     * Full access (Read/Write) to the device's contacts.
     *
     * - **Android**: `READ_CONTACTS` + `WRITE_CONTACTS`.
     * - **iOS**: `NSContactsUsageDescription`.
     */
    CONTACTS,

    /**
     * Read-only access to the device's contacts.
     *
     * - **Android**: `READ_CONTACTS`.
     * - **iOS**: `NSContactsUsageDescription`.
     */
    READ_CONTACTS,

    /**
     * Access to motion sensors and activity recognition.
     *
     * - **Android**: `ACTIVITY_RECOGNITION` (API 29+).
     * - **iOS**: `NSMotionUsageDescription`.
     */
    MOTION,

    /**
     * Full access (Read/Write) to calendar events.
     *
     * - **Android**: `READ_CALENDAR` + `WRITE_CALENDAR`.
     * - **iOS**: `NSCalendarsUsageDescription`.
     */
    CALENDAR,

    /**
     * Read-only access to calendar events.
     *
     * - **Android**: `READ_CALENDAR`.
     * - **iOS**: `NSCalendarsUsageDescription`.
     */
    READ_CALENDAR,

    /**
     * Permission to scan for nearby Wi-Fi devices.
     *
     * - **Android**: Maps to `NEARBY_WIFI_DEVICES` (API 33+) or `ACCESS_FINE_LOCATION` (API < 33).
     * - **iOS**: No-op (always GRANTED).
     */
    NEARBY_WIFI_DEVICES,

    /**
     * Permission to communicate with devices on the local network (LAN) — smart-home
     * devices, casting receivers, printers.
     *
     * - **Android**: Maps to `ACCESS_LOCAL_NETWORK` (API 37+ / Android 17 — a runtime
     *   permission in the `NEARBY_DEVICES` group whose enforcement is mandatory for apps
     *   targeting 37; users who already granted another NEARBY_DEVICES permission are not
     *   prompted again). No-op (always GRANTED) below API 37.
     * - **iOS**: No-op (always GRANTED) — iOS has no API to query or explicitly request
     *   local-network authorization; the OS prompts automatically on the first LAN access
     *   when `NSLocalNetworkUsageDescription` is present in Info.plist.
     */
    LOCAL_NETWORK;

    /**
     * Unique identifier for this permission.
     *
     * Returns the enum constant name (e.g., "CAMERA").
     */
    override val identifier: String get() = name
}
