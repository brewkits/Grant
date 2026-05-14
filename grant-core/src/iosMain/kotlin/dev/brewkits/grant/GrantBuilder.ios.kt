package dev.brewkits.grant

import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.handlers.AVPermissionHandler
import dev.brewkits.grant.handlers.BluetoothPermissionHandler
import dev.brewkits.grant.handlers.CalendarPermissionHandler
import dev.brewkits.grant.handlers.ContactsPermissionHandler
import dev.brewkits.grant.handlers.IosPermissionHandler
import dev.brewkits.grant.handlers.LocationPermissionHandler
import dev.brewkits.grant.handlers.MotionPermissionHandler
import dev.brewkits.grant.handlers.NotificationPermissionHandler
import dev.brewkits.grant.handlers.PhotoPermissionHandler

/*
 * iOS opt-in handler registration.
 *
 * Each `actual fun` in this file references ONLY the handler classes for its
 * own permission family. The K/N linker walks the call graph; if a consumer
 * never calls — e.g. — `contacts()`, then [ContactsPermissionHandler] is
 * unreachable, the `import platform.Contacts.*` declaration in that file is
 * unreachable, and the Contacts framework is not linked. Apple's static
 * analyzer then has no basis to demand an `NSContactsUsageDescription` key.
 *
 * Do NOT consolidate these into a single function or a map keyed on AppGrant.
 * Any code shape that references every handler from one site defeats the
 * whole purpose of this file — DCE will keep them all.
 */

// Shared platform delegates are held as `by lazy` private properties on the file
// so that location and bluetooth share one CLLocationManager / CBCentralManager
// instance per process (matching legacy behavior). Each property only references
// its own delegate class, so DCE rules still apply per-permission.
private val sharedLocationDelegate by lazy { LocationManagerDelegate() }
private val sharedBluetoothDelegate by lazy { BluetoothManagerDelegate() }

actual fun GrantBuilder.camera() {
    register(AppGrant.CAMERA) { AVPermissionHandler.camera() as IosPermissionHandler }
}

actual fun GrantBuilder.microphone() {
    register(AppGrant.MICROPHONE) { AVPermissionHandler.microphone() as IosPermissionHandler }
}

actual fun GrantBuilder.gallery() {
    val factory: () -> Any = { PhotoPermissionHandler() }
    register(AppGrant.GALLERY, factory)
    register(AppGrant.GALLERY_IMAGES_ONLY, factory)
    register(AppGrant.GALLERY_VIDEO_ONLY, factory)
}

actual fun GrantBuilder.storage() {
    // STORAGE on iOS is a no-op (sandbox; always GRANTED) but it dispatches through
    // the Photos handler for parity with legacy behavior.
    register(AppGrant.STORAGE) { PhotoPermissionHandler() }
}

actual fun GrantBuilder.location() {
    register(AppGrant.LOCATION) {
        LocationPermissionHandler(forAlways = false, delegate = sharedLocationDelegate)
    }
}

actual fun GrantBuilder.locationAlways() {
    register(AppGrant.LOCATION_ALWAYS) {
        LocationPermissionHandler(forAlways = true, delegate = sharedLocationDelegate)
    }
}

actual fun GrantBuilder.notification() {
    register(AppGrant.NOTIFICATION) { NotificationPermissionHandler() }
}

actual fun GrantBuilder.scheduleExactAlarm() {
    register(AppGrant.SCHEDULE_EXACT_ALARM) { AlwaysGrantedIosHandler }
}

actual fun GrantBuilder.bluetooth() {
    val factory: () -> Any = { BluetoothPermissionHandler(delegate = sharedBluetoothDelegate) }
    register(AppGrant.BLUETOOTH, factory)
    register(AppGrant.BLUETOOTH_ADVERTISE, factory)
}

actual fun GrantBuilder.contacts() {
    val factory: () -> Any = { ContactsPermissionHandler() }
    register(AppGrant.CONTACTS, factory)
    register(AppGrant.READ_CONTACTS, factory)
}

actual fun GrantBuilder.calendar() {
    val factory: () -> Any = { CalendarPermissionHandler() }
    register(AppGrant.CALENDAR, factory)
    register(AppGrant.READ_CALENDAR, factory)
}

actual fun GrantBuilder.motion() {
    register(AppGrant.MOTION) { MotionPermissionHandler() }
}

actual fun GrantBuilder.nearbyWifiDevices() {
    register(AppGrant.NEARBY_WIFI_DEVICES) { AlwaysGrantedIosHandler }
}

/**
 * Singleton for iOS permissions that are always GRANTED without a dialog
 * (e.g. [AppGrant.SCHEDULE_EXACT_ALARM], [AppGrant.NEARBY_WIFI_DEVICES]).
 * Defined here (not inside [dev.brewkits.grant.impl.PlatformGrantDelegate])
 * so that the per-permission `actual fun`s above can reference it without
 * pulling the delegate's other transitive imports.
 */
internal object AlwaysGrantedIosHandler : IosPermissionHandler {
    override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
    override suspend fun request(): GrantStatus = GrantStatus.GRANTED
}
