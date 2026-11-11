package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import kotlin.coroutines.resume

private const val TAG = "AVPermissionHandler"

/**
 * Handles Camera and Microphone permissions via AVFoundation.
 *
 * Lives in `grant-camera`, not `grant-core` (Issue #91). The `AVCaptureDevice` authorization
 * calls below are what App Store Connect looks for when it asks for NSCameraUsageDescription /
 * NSMicrophoneUsageDescription, so an app that never captures media should not carry them.
 * (Earlier KDoc called AVFoundation "safe" to link for that reason. That is true of playback
 * APIs, not of these.)
 *
 * Use the factory functions [camera] and [microphone] to create instances.
 */
internal class AVPermissionHandler private constructor(
    private val mediaType: String,
    private val plistKey: String
) : PermissionHandler {

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, plistKey)) return GrantStatus.DENIED_ALWAYS
        return when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else                               -> GrantStatus.NOT_DETERMINED
        }
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, plistKey)) return GrantStatus.DENIED_ALWAYS
        return suspendCancellableCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
                mainContinuation<Boolean> { g ->
                    cont.resume(if (g) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
                }.invoke(granted)
            }
        }
    }

    companion object {
        /** Returns a handler configured for [AppGrant.CAMERA]. */
        fun camera(): AVPermissionHandler =
            AVPermissionHandler(AVMediaTypeVideo!!, "NSCameraUsageDescription")

        /** Returns a handler configured for [AppGrant.MICROPHONE]. */
        fun microphone(): AVPermissionHandler =
            AVPermissionHandler(AVMediaTypeAudio!!, "NSMicrophoneUsageDescription")
    }
}
