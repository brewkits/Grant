package dev.brewkits.grant

/**
 * DSL builder used by [GrantFactory.create] to opt into the handlers an app needs.
 *
 * On iOS, the Kotlin/Native linker walks the call graph and links every class that is
 * reachable from compiled code. Historically `PlatformGrantDelegate` referenced every
 * handler class in a single `when` expression, which forced the linker to keep every
 * handler — and every `import platform.X.*` declaration inside those handlers — alive
 * in the final binary. Apple's static analyzer then required `NSUsageDescription`
 * keys for frameworks the app never actually uses (CoreMotion, EventKit, Contacts, …),
 * causing App Store rejections. See [issue #38](https://github.com/brewkits/Grant/issues/38).
 *
 * The fix is opt-in registration. Each per-permission extension function (e.g.
 * [bluetooth], [location], [contacts]) lives in its own `actual` declaration and only
 * references the handlers it needs. When a consumer omits a permission, the extension
 * function is unreachable and the K/N linker strips the handler — along with its
 * framework imports — from the binary.
 *
 * Example:
 * ```kotlin
 * val grantManager = GrantFactory.create {
 *     location()
 *     locationAlways()
 *     bluetooth()
 *     notification()
 *     // contacts(), calendar(), motion() not called → those handlers are not linked.
 * }
 * ```
 *
 * @property registrations Map of [AppGrant] → factory for a platform-specific handler.
 *   The value type is [Any] so commonMain doesn't depend on a platform-specific handler
 *   interface; platform-specific factory implementations cast on use.
 */
class GrantBuilder internal constructor() {
    /**
     * Internal registry of permission → handler factory.
     *
     * The value type is `() -> Any` because commonMain cannot reference
     * the iOS-specific `IosPermissionHandler` interface. Platform code casts on use.
     */
    internal val registrations: MutableMap<AppGrant, () -> Any> = mutableMapOf()

    /**
     * Registers a handler factory for a single [AppGrant].
     *
     * Called by the per-permission extension functions ([location], [bluetooth], …).
     * Most consumers should call those instead of this directly.
     */
    fun register(grant: AppGrant, handlerFactory: () -> Any) {
        registrations[grant] = handlerFactory
    }
}

/**
 * Registers handlers for every [AppGrant]. Used by the deprecated no-arg
 * [GrantFactory.create] overload to preserve backward-compatible behavior:
 * every handler is linked, every framework import is kept, and the existing
 * workaround of declaring all `NSUsageDescription` keys continues to work.
 *
 * New code should call only the specific permission extensions it needs.
 */
fun GrantBuilder.registerAll() {
    camera()
    microphone()
    gallery()
    storage()
    location()
    locationAlways()
    notification()
    scheduleExactAlarm()
    bluetooth()
    contacts()
    calendar()
    motion()
    nearbyWifiDevices()
}

// ---------------------------------------------------------------------------
// Per-permission extension declarations.
//
// Each declaration is `expect` here and `actual` in iosMain / androidMain.
// On iOS, the `actual` body references only the handler classes for that
// permission family — the key property that lets K/N DCE remove unused
// handlers (and their `import platform.X.*` statements) from the binary.
// ---------------------------------------------------------------------------

/** Camera (iOS: AVFoundation / NSCameraUsageDescription). */
expect fun GrantBuilder.camera()

/** Microphone (iOS: AVFoundation / NSMicrophoneUsageDescription). */
expect fun GrantBuilder.microphone()

/**
 * Photo library — full, images-only, videos-only, and STORAGE (no-op on iOS).
 *
 * Registers handlers for [AppGrant.GALLERY], [AppGrant.GALLERY_IMAGES_ONLY],
 * [AppGrant.GALLERY_VIDEO_ONLY]. iOS uses NSPhotoLibraryUsageDescription.
 */
expect fun GrantBuilder.gallery()

/**
 * Storage. No-op on iOS (sandbox; always GRANTED) but routed through the same
 * Photos handler so that [AppGrant.STORAGE] dispatches correctly.
 */
expect fun GrantBuilder.storage()

/** Foreground location (iOS: NSLocationWhenInUseUsageDescription). */
expect fun GrantBuilder.location()

/** Background location (iOS: NSLocationAlwaysAndWhenInUseUsageDescription). */
expect fun GrantBuilder.locationAlways()

/** Push & local notifications (iOS: UserNotifications). */
expect fun GrantBuilder.notification()

/** Always GRANTED on iOS — no framework linked, but covers Android API 31+. */
expect fun GrantBuilder.scheduleExactAlarm()

/**
 * Bluetooth — central + advertise. Registers [AppGrant.BLUETOOTH] and
 * [AppGrant.BLUETOOTH_ADVERTISE]. iOS: CoreBluetooth /
 * NSBluetoothAlwaysUsageDescription.
 */
expect fun GrantBuilder.bluetooth()

/**
 * Contacts — full and read-only. Registers [AppGrant.CONTACTS] and
 * [AppGrant.READ_CONTACTS]. iOS: Contacts / NSContactsUsageDescription.
 */
expect fun GrantBuilder.contacts()

/**
 * Calendar — full and read-only. Registers [AppGrant.CALENDAR] and
 * [AppGrant.READ_CALENDAR]. iOS: EventKit / NSCalendarsUsageDescription.
 */
expect fun GrantBuilder.calendar()

/** Motion & activity (iOS: CoreMotion / NSMotionUsageDescription). */
expect fun GrantBuilder.motion()

/** Always GRANTED on iOS — no framework linked. */
expect fun GrantBuilder.nearbyWifiDevices()
