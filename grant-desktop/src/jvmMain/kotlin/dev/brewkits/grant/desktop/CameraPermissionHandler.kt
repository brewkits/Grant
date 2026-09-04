package dev.brewkits.grant.desktop

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.handlers.DesktopPermissionHandler
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DesktopAVMediaHandler"

/**
 * Camera and microphone permission via AVFoundation, through the Kotlin/Native bridge in
 * `CameraBridge.kt`/`macosMain`. Camera is Tier 2's first slice (ROADMAP.md v2.6.0) — verified
 * end-to-end on real hardware (a real consent dialog, Allow, status flips to GRANTED).
 * Microphone reuses the identical bridge code path (`avStatus`/`avRequestBlocking` in
 * `CameraBridge.kt`), not a second, separately-risked implementation, so what was actually
 * verified for camera — the ABI, the completion-handler bridging, the blocking/re-read pattern
 * — is exactly what microphone runs through too.
 *
 * One class, not two: mirrors `AVPermissionHandler`'s shape on iOS (`camera()`/`microphone()`
 * factory functions over one implementation), rather than duplicating the mapping logic.
 */
internal class AVMediaPermissionHandler private constructor(
    private val checkRaw: () -> Int,
    private val requestRawBlocking: () -> Int,
) : DesktopPermissionHandler {

    override fun checkStatus(): GrantStatus = mapAuthorizationStatus(checkRaw())

    override suspend fun request(): GrantStatus {
        // *_request_blocking() blocks the calling thread until the macOS consent dialog
        // resolves. Dispatchers.IO specifically (not Default): this is a thread parked on a
        // native wait, not CPU work, and Default's pool is sized to the core count — parking a
        // core-count-sized pool thread for however long a human takes to answer a dialog could
        // starve unrelated work. It must also never run inline on a UI-bound dispatcher, which
        // would freeze the app for the dialog's lifetime, and never on the main thread, where
        // an AVFoundation completion delivered to the main queue would deadlock against it.
        // The Kotlin/Native side blocks on NSCondition; see CameraBridge.kt.
        val rawStatus = withContext(Dispatchers.IO) { requestRawBlocking() }
        return mapAuthorizationStatus(rawStatus)
    }

    private fun mapAuthorizationStatus(rawStatus: Int): GrantStatus = when (rawStatus) {
        0 -> GrantStatus.NOT_DETERMINED
        1, 2 -> GrantStatus.DENIED_ALWAYS // 1 = Restricted (e.g. parental controls), 2 = Denied
        3 -> GrantStatus.GRANTED
        else -> {
            GrantLogger.w(TAG, "Unrecognized AVAuthorizationStatus raw value: $rawStatus")
            GrantStatus.NOT_DETERMINED
        }
    }

    companion object {
        fun camera(bridge: GrantDesktopBridgeLibrary): AVMediaPermissionHandler =
            AVMediaPermissionHandler(bridge::grant_camera_status, bridge::grant_camera_request_blocking)

        fun microphone(bridge: GrantDesktopBridgeLibrary): AVMediaPermissionHandler =
            AVMediaPermissionHandler(bridge::grant_microphone_status, bridge::grant_microphone_request_blocking)
    }
}
