package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.runOnMain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import platform.AVFoundation.*
import platform.CoreLocation.*
import platform.Foundation.*
import platform.Photos.*
import platform.UIKit.*
import platform.UserNotifications.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PlatformGrantDelegate(
    private val store: GrantStore
) {
    private val mutexMap = mutableMapOf<String, Mutex>()
    private fun getMutexFor(identifier: String): Mutex = mutexMap.getOrPut(identifier) { Mutex() }
    private val statusCacheMap = mutableMapOf<String, Pair<GrantStatus, Long>>()
    private companion object { const val STATUS_CACHE_TTL_MS = 1000L }
    private fun getMonotonicTimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
            if (getMonotonicTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) return cachedStatus
        }
        val status = checkStatusInternal(grant)
        statusCacheMap[identifier] = status to getMonotonicTimeMillis()
        return status
    }

    private suspend fun checkStatusInternal(grant: GrantPermission): GrantStatus = runOnMain {
        if (grant is RawPermission) return@runOnMain GrantStatus.DENIED
        when (grant as AppGrant) {
            AppGrant.CAMERA -> checkCameraStatus()
            AppGrant.GALLERY, AppGrant.STORAGE, AppGrant.GALLERY_IMAGES_ONLY, AppGrant.GALLERY_VIDEO_ONLY -> checkPhotoStatus()
            AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS -> checkLocationStatus(grant)
            AppGrant.NOTIFICATION -> checkNotificationStatus()
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus = getMutexFor(grant.identifier).withLock { requestInternal(grant) }
    actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        val results = mutableMapOf<GrantPermission, GrantStatus>()
        grants.forEach { results[it] = request(it) }
        return results
    }

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        val status = checkStatus(grant)
        if (status == GrantStatus.GRANTED || status == GrantStatus.PARTIAL_GRANTED) return status
        
        // Simplified mapping for demo purposes
        return GrantStatus.DENIED
    }

    actual fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url != null) UIApplication.sharedApplication.openURL(url)
    }

    // Exposed for testing
    internal fun checkCameraStatus(): GrantStatus {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        return when (status) {
            AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    internal fun checkPhotoStatus(): GrantStatus {
        val status = PHPhotoLibrary.authorizationStatus()
        return when (status) {
            PHAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            PHAuthorizationStatusLimited -> GrantStatus.PARTIAL_GRANTED
            PHAuthorizationStatusDenied, PHAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            else -> GrantStatus.NOT_DETERMINED
        }
    }
    
    private fun checkLocationStatus(grant: AppGrant): GrantStatus = GrantStatus.NOT_DETERMINED
    private fun checkNotificationStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
}
