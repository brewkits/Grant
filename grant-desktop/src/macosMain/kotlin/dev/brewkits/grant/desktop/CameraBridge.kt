@file:Suppress("unused") // called from the JVM side over JNA, not from Kotlin/Native code

package dev.brewkits.grant.desktop

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSCondition

/**
 * The Kotlin/Native half of the Tier 2 macOS bridge (ROADMAP.md v2.6.0). Compiled as a
 * `.dylib` (`binaries.sharedLib()` in `build.gradle.kts`) and loaded by the JVM side over JNA
 * (`GrantDesktopBridge.kt`, `jvmMain`) — see that file for why this exists as a dylib instead
 * of hand-rolled JNA `objc_msgSend` calls: `requestAccessForMediaType`'s completion handler is
 * a plain Kotlin lambda here, because the Kotlin/Native compiler generates the Objective-C
 * block for it. No Block_literal struct is built by hand anywhere in this codebase.
 *
 * Every exported function returns the **raw `AVAuthorizationStatus` integer** —
 * 0 = NotDetermined, 1 = Restricted, 2 = Denied, 3 = Authorized — rather than inventing a
 * second status vocabulary. `GrantDesktopBridge.kt` (JVM side) is the single place that maps
 * this to [dev.brewkits.grant.GrantStatus]; duplicating that mapping here would let the two
 * sides silently drift.
 *
 * [grantCameraRequestBlocking] blocks the calling native thread until the completion handler
 * fires, using [NSCondition] rather than a Kotlin coroutine: this file has no coroutine
 * dispatcher of its own, and the JVM caller (`GrantDesktopBridge.kt`) is the one that already
 * runs this off its main thread and turns the blocking C call back into a `suspend` function.
 * That keeps this file's job to exactly one thing — bridge the ObjC call — and keeps the
 * ObjC-completion-handler → JVM-callback boundary from ever being crossed at all, per the
 * architecture note in ROADMAP.md v2.6.0.
 */
@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@CName("grant_camera_status")
public fun grantCameraStatus(): Int {
    val status: AVAuthorizationStatus = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo!!)
    return status.toInt()
}

@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@CName("grant_camera_request_blocking")
public fun grantCameraRequestBlocking(): Int {
    val condition = NSCondition()
    var resultGranted: Boolean? = null

    condition.lock()
    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo!!) { granted ->
        condition.lock()
        resultGranted = granted
        condition.signal()
        condition.unlock()
    }
    while (resultGranted == null) {
        condition.wait()
    }
    condition.unlock()

    // Re-read the real status rather than trusting the boolean directly — matches the
    // convention grant-core's iOS handlers already follow (see CalendarPermissionHandler.kt's
    // KDoc): re-reading after the callback is the source of truth, the boolean is a summary.
    return grantCameraStatus()
}
