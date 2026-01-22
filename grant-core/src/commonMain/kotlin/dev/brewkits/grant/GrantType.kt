package dev.brewkits.grant

/**
 * Defines all grants used in the application.
 *
 * This enum provides a clean, platform-agnostic abstraction over
 * Android and iOS grant systems.
 *
 * **Extensibility**: To add new grants:
 * 1. Add new enum entry here
 * 2. Update platform-specific implementations in PlatformGrantDelegate
 * 3. Add required Info.plist keys (iOS) or AndroidManifest grants (Android)
 */
enum class AppGrant {
    /**
     * Camera grant for photo/video capture
     * - Android: CAMERA
     * - iOS: NSCameraUsageDescription
     */
    CAMERA,

    /**
     * Photo library/gallery access
     * - Android: READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE
     * - iOS: NSPhotoLibraryUsageDescription
     */
    GALLERY,

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

    // TODO: Calendar grant - EventKit constants need to be verified for KMP
    // Uncomment after fixing iOS EventKit bindings
    // /**
    //  * Calendar access
    //  * - Android: READ_CALENDAR / WRITE_CALENDAR
    //  * - iOS: NSCalendarsUsageDescription (EventKit)
    //  */
    // CALENDAR,
}
