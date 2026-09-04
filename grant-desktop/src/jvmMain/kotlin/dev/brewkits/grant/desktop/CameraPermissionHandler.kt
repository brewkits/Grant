package dev.brewkits.grant.desktop

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.handlers.DesktopPermissionHandler
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DesktopCameraHandler"

/**
 * Camera permission via AVFoundation, through the Kotlin/Native bridge in
 * `CameraBridge.kt`/`macosMain`. This is Tier 2's first slice (ROADMAP.md v2.6.0) — the only
 * permission wired end-to-end and verified on a real Mac so far.
 */
internal class CameraPermissionHandler(
    private val bridge: GrantDesktopBridgeLibrary,
) : DesktopPermissionHandler {

    override fun checkStatus(): GrantStatus = mapAuthorizationStatus(bridge.grant_camera_status())

    override suspend fun request(): GrantStatus {
        // grant_camera_request_blocking() blocks the calling native thread until the macOS
        // consent dialog resolves — run it off the JVM's default dispatcher's worker threads,
        // never inline, so a caller on a UI-bound dispatcher isn't frozen for the dialog's
        // lifetime.
        val rawStatus = withContext(Dispatchers.IO) { bridge.grant_camera_request_blocking() }
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
}
