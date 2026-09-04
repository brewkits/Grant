package dev.brewkits.grant.js

import kotlin.js.Promise

/**
 * Minimal `external` bindings for the browser APIs `PlatformGrantDelegate.web.kt` actually
 * calls. Kept in one file, one declaration per real Web Platform API, so every binding here
 * is auditable against MDN rather than buried inside delegate logic.
 *
 * Lives in `webMain`, shared by both the classic `js` target and `wasmJs` (Compose Multiplatform
 * Web's actual target) — see the KMP 2.2.20+ `webMain` source set the default hierarchy template
 * now provides. Every external interface extends [JsAny]: that is required on `wasmJs` and is a
 * harmless, ignored marker on classic `js`, which is what makes one binding file cover both
 * targets instead of two divergent copies drifting apart.
 *
 * `dynamic` (Kotlin/JS's untyped escape hatch) is deliberately unused here — it does not exist
 * on `wasmJs` at all, so keeping this file `dynamic`-free is what makes the shared source set
 * possible rather than a stylistic choice. Where a JS object literal must be constructed (e.g.
 * `{ name: "camera" }`), a small typed `js("(...) => ...")`-bodied factory function does it; that
 * mechanism is common to both targets too.
 *
 * Deliberately hand-written rather than pulled from `kotlinx-browser`: as of this writing the
 * Kotlin-version-compatible build of that library is unpublished (its README states "not yet
 * intended to use by end user"); the last published artifact, `0.3`, predates it, and it has no
 * `wasmJs` target regardless. These bindings cover exactly four browser surfaces and are small
 * enough to keep correct without depending on an experimental, unreleased-for-this-toolchain
 * library.
 */

// ── Permissions API — https://developer.mozilla.org/en-US/docs/Web/API/Permissions/query ──
// Status-only: query() never triggers a prompt. Browser support for the permission *names*
// below is inconsistent — Firefox throws a TypeError for "camera" and "microphone" — so every
// call site wraps this in try/catch and treats a thrown error as "status unknown", not as
// "denied". See PlatformGrantDelegate.web.kt.

internal external interface PermissionStatusJs : JsAny {
    val state: String // "granted" | "denied" | "prompt"
}

internal external interface PermissionDescriptorJs : JsAny {
    val name: String
}

/** `{ name: name }` — the sole shape `Permissions.query()` accepts. */
internal fun permissionDescriptor(name: String): PermissionDescriptorJs = js("({ name: name })")

internal external interface PermissionsApi : JsAny {
    fun query(descriptor: PermissionDescriptorJs): Promise<PermissionStatusJs>
}

// ── MediaDevices.getUserMedia — the actual camera/microphone consent prompt ──
// https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia
// Requires a secure context (HTTPS or localhost); the browser rejects the returned Promise
// with a NotAllowedError on denial, which is how PlatformGrantDelegate.web.kt tells DENIED
// apart from a hardware/context failure.

internal external interface MediaStreamTrackJs : JsAny {
    fun stop()
}

internal external interface MediaStreamJs : JsAny {
    fun getTracks(): JsArray<MediaStreamTrackJs>
}

internal external interface MediaDeviceInfoJs : JsAny {
    val kind: String // "videoinput" | "audioinput" | "audiooutput"
}

internal external interface MediaConstraintsJs : JsAny {
    val video: Boolean
    val audio: Boolean
}

/** `{ video: video, audio: audio }` — the constraints shape `getUserMedia()` accepts. */
internal fun mediaConstraints(video: Boolean, audio: Boolean): MediaConstraintsJs =
    js("({ video: video, audio: audio })")

internal external interface MediaDevicesApi : JsAny {
    fun getUserMedia(constraints: MediaConstraintsJs): Promise<MediaStreamJs>

    /**
     * Device *presence* (kind == "videoinput") is visible without a permission grant; device
     * *labels* are blank until granted. Used only as a "does camera hardware exist" signal —
     * see PlatformServiceDelegate.web.kt.
     */
    fun enumerateDevices(): Promise<JsArray<MediaDeviceInfoJs>>
}

// ── Geolocation — https://developer.mozilla.org/en-US/docs/Web/API/Geolocation ──
// getCurrentPosition's callback pair (success, error) is the actual consent prompt; there is
// no separate "request" call — the first invocation prompts, subsequent ones after a grant do
// not.

internal external interface GeolocationPositionJs : JsAny

internal external interface GeolocationPositionErrorJs : JsAny {
    val code: Int // 1 = PERMISSION_DENIED, 2 = POSITION_UNAVAILABLE, 3 = TIMEOUT
}

internal external interface GeolocationApi : JsAny {
    fun getCurrentPosition(
        success: (GeolocationPositionJs) -> Unit,
        error: (GeolocationPositionErrorJs) -> Unit,
    )
}

internal external interface NavigatorJs : JsAny {
    val permissions: PermissionsApi
    val mediaDevices: MediaDevicesApi
    val geolocation: GeolocationApi
}

@JsName("navigator")
internal external val navigatorJs: NavigatorJs

// ── Notification — https://developer.mozilla.org/en-US/docs/Web/API/Notification ──
// A global, not reached through `navigator`. requestPermission() is the actual prompt;
// browsers that predate the Promise-returning overload accept a callback, but every browser
// still shipping in 2026 supports the Promise form.

internal external interface NotificationCtorJs : JsAny {
    val permission: String // "granted" | "denied" | "default"
    fun requestPermission(): Promise<JsString>
}

@JsName("Notification")
internal external val notificationJs: NotificationCtorJs
