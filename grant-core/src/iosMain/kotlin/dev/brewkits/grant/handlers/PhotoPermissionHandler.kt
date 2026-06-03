package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

private const val TAG = "PhotoPermissionHandler"

/**
 * Handles Gallery / Photo Library permissions via Photos framework.
 *
 * Uses the non-deprecated [PHPhotoLibrary.requestAuthorizationForAccessLevel]
 * API on iOS 14+ and falls back to the legacy API on older versions.
 *
 * PHAuthorizationStatusLimited (iOS 14+) maps to [GrantStatus.PARTIAL_GRANTED]
 * to inform the caller that the user selected only specific photos.
 *
 * iOS version detection:
 * Previously used `operatingSystemVersionString` which Apple explicitly documents
 * as "not suitable for parsing." Now uses `operatingSystemVersion.majorVersion`
 * (a structured `NSOperatingSystemVersion` value) which is reliable and locale-independent.
 */
internal class PhotoPermissionHandler : PermissionHandler {

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, "NSPhotoLibraryUsageDescription")) return GrantStatus.DENIED_ALWAYS

        // Use non-deprecated API on iOS 14+ (authorizationStatus() is deprecated in iOS 14).
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        val isIos14OrNewer = NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
            kotlinx.cinterop.cValue {
                majorVersion = 14
                minorVersion = 0
                patchVersion = 0
            }
        )

        val status = if (isIos14OrNewer) {
            PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
        } else {
            @Suppress("DEPRECATION")
            PHPhotoLibrary.authorizationStatus()
        }

        return when (status) {
            PHAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
            PHAuthorizationStatusLimited       -> GrantStatus.PARTIAL_GRANTED
            PHAuthorizationStatusDenied,
            PHAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
            PHAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else                               -> GrantStatus.NOT_DETERMINED
        }
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, "NSPhotoLibraryUsageDescription")) return GrantStatus.DENIED_ALWAYS

        // Use isOperatingSystemAtLeastVersion() — the documented, locale-safe API.
        // NSOperatingSystemVersion is a C struct that requires cValue<> in Kotlin/Native.
        @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
        val isIos14OrNewer = NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
            kotlinx.cinterop.cValue {
                majorVersion = 14
                minorVersion = 0
                patchVersion = 0
            }
        )

        return suspendCancellableCoroutine { cont ->
            if (isIos14OrNewer) {
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
                    val result = when (status) {
                        PHAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
                        PHAuthorizationStatusLimited       -> GrantStatus.PARTIAL_GRANTED
                        PHAuthorizationStatusDenied,
                        PHAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
                        PHAuthorizationStatusNotDetermined -> GrantStatus.DENIED
                        else                               -> GrantStatus.DENIED
                    }
                    mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
                }
            } else {
                @Suppress("DEPRECATION")
                PHPhotoLibrary.requestAuthorization { status ->
                    val result = when (status) {
                        PHAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
                        PHAuthorizationStatusLimited       -> GrantStatus.PARTIAL_GRANTED
                        PHAuthorizationStatusDenied,
                        PHAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
                        else                               -> GrantStatus.DENIED
                    }
                    mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
                }
            }
        }
    }
}
