package dev.brewkits.grant

/*
 * Android `actual` extensions for [GrantBuilder].
 *
 * Android doesn't need per-permission handler classes — the
 * `AndroidGrantDelegate` dispatches directly through Activity Result API +
 * `ContextCompat.checkSelfPermission`. The registry produced by [GrantBuilder]
 * is therefore ignored on Android (see `createPlatformDelegateWithRegistry`),
 * but we still register a sentinel value so the registry isn't empty —
 * primarily so that `commonTest` tests asserting which permissions were
 * registered pass on Android the same way they do on iOS.
 */

private val ANDROID_NOOP: () -> Any = { Unit }

actual fun GrantBuilder.camera() {
    register(AppGrant.CAMERA, ANDROID_NOOP)
}

actual fun GrantBuilder.microphone() {
    register(AppGrant.MICROPHONE, ANDROID_NOOP)
}

actual fun GrantBuilder.gallery() {
    register(AppGrant.GALLERY, ANDROID_NOOP)
    register(AppGrant.GALLERY_IMAGES_ONLY, ANDROID_NOOP)
    register(AppGrant.GALLERY_VIDEO_ONLY, ANDROID_NOOP)
}

actual fun GrantBuilder.storage() {
    register(AppGrant.STORAGE, ANDROID_NOOP)
}

actual fun GrantBuilder.location() {
    register(AppGrant.LOCATION, ANDROID_NOOP)
}

actual fun GrantBuilder.locationAlways() {
    register(AppGrant.LOCATION_ALWAYS, ANDROID_NOOP)
}

actual fun GrantBuilder.notification() {
    register(AppGrant.NOTIFICATION, ANDROID_NOOP)
}

actual fun GrantBuilder.scheduleExactAlarm() {
    register(AppGrant.SCHEDULE_EXACT_ALARM, ANDROID_NOOP)
}

actual fun GrantBuilder.bluetooth() {
    register(AppGrant.BLUETOOTH, ANDROID_NOOP)
    register(AppGrant.BLUETOOTH_ADVERTISE, ANDROID_NOOP)
}

actual fun GrantBuilder.contacts() {
    register(AppGrant.CONTACTS, ANDROID_NOOP)
    register(AppGrant.READ_CONTACTS, ANDROID_NOOP)
}

actual fun GrantBuilder.calendar() {
    register(AppGrant.CALENDAR, ANDROID_NOOP)
    register(AppGrant.READ_CALENDAR, ANDROID_NOOP)
}

actual fun GrantBuilder.motion() {
    register(AppGrant.MOTION, ANDROID_NOOP)
}

actual fun GrantBuilder.nearbyWifiDevices() {
    register(AppGrant.NEARBY_WIFI_DEVICES, ANDROID_NOOP)
}
