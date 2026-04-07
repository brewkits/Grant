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
import platform.AVFoundation.*
import platform.CoreBluetooth.*
import platform.CoreLocation.*
import platform.CoreMotion.*
import platform.Foundation.*
import platform.Photos.*
import platform.UserNotifications.*
import platform.UIKit.*
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class PlatformGrantDelegate(
    private val store: GrantStore
) {
    private val mutexMap = mutableMapOf<String, Mutex>()

    private fun getMutexFor(identifier: String): Mutex {
        return mutexMap.getOrPut(identifier) { Mutex() }
    }

    private val statusCacheMap = mutableMapOf<String, Pair<GrantStatus, Long>>()

    private companion object {
        const val STATUS_CACHE_TTL_MS = 1000L
    }

    private fun getMonotonicTimeMillis(): Long {
        return (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
    }

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
            if (getMonotonicTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) {
                return cachedStatus
            }
        }

        val status = checkStatusInternal(grant)
        statusCacheMap[identifier] = status to getMonotonicTimeMillis()
        return status
    }

    private suspend fun checkStatusInternal(grant: GrantPermission): GrantStatus = runOnMain {
        if (grant is RawPermission) {
            val usageKey = grant.iosUsageKey
            if (usageKey != null && NSBundle.mainBundle.objectForInfoDictionaryKey(usageKey) == null) {
                return@runOnMain GrantStatus.DENIED_ALWAYS
            }
            return@runOnMain GrantStatus.DENIED // iOS doesn't have a generic check for raw strings
        }

        val appGrant = grant as AppGrant
        return@runOnMain when (appGrant) {
            AppGrant.CAMERA -> checkCameraStatus()
            AppGrant.GALLERY, AppGrant.STORAGE, AppGrant.GALLERY_IMAGES_ONLY, AppGrant.GALLERY_VIDEO_ONLY -> checkPhotoStatus()
            AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS -> checkLocationStatus(appGrant)
            AppGrant.MICROPHONE -> checkMicrophoneStatus()
            AppGrant.NOTIFICATION -> checkNotificationStatus()
            AppGrant.BLUETOOTH, AppGrant.BLUETOOTH_ADVERTISE -> checkBluetoothStatus()
            AppGrant.MOTION -> checkMotionStatus()
            AppGrant.CONTACTS, AppGrant.READ_CONTACTS -> checkContactsStatus()
            AppGrant.CALENDAR, AppGrant.READ_CALENDAR -> checkCalendarStatus()
            AppGrant.SCHEDULE_EXACT_ALARM -> GrantStatus.GRANTED
        }
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus {
        return getMutexFor(grant.identifier).withLock {
            requestInternal(grant)
        }
    }

    actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        val results = mutableMapOf<GrantPermission, GrantStatus>()
        for (grant in grants) {
            results[grant] = request(grant)
        }
        return results
    }

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        val currentStatus = checkStatus(grant)
        if (currentStatus == GrantStatus.GRANTED || currentStatus == GrantStatus.PARTIAL_GRANTED) return currentStatus

        if (grant is RawPermission) return GrantStatus.DENIED

        val appGrant = grant as AppGrant
        val status = when (appGrant) {
            AppGrant.CAMERA -> requestCameraGrant()
            AppGrant.GALLERY, AppGrant.STORAGE, AppGrant.GALLERY_IMAGES_ONLY, AppGrant.GALLERY_VIDEO_ONLY -> requestPhotoGrant()
            AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS -> requestLocationGrant(appGrant)
            AppGrant.MICROPHONE -> requestMicrophoneGrant()
            AppGrant.NOTIFICATION -> requestNotificationGrant()
            AppGrant.BLUETOOTH, AppGrant.BLUETOOTH_ADVERTISE -> requestBluetoothGrant()
            AppGrant.MOTION -> requestMotionGrant()
            AppGrant.CONTACTS, AppGrant.READ_CONTACTS -> requestContactsStatus()
            AppGrant.CALENDAR, AppGrant.READ_CALENDAR -> requestCalendarStatus()
            AppGrant.SCHEDULE_EXACT_ALARM -> GrantStatus.GRANTED
        }

        if (status != GrantStatus.GRANTED && status != GrantStatus.PARTIAL_GRANTED) {
            store.setRequested(appGrant)
            store.setStatus(appGrant, status)
        } else {
            store.clear(appGrant)
        }
        return status
    }

    actual fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url != null) {
            UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
        }
    }

    // --- Implementation helpers (Omitted for brevity, but should be present in real file) ---
    private fun checkCameraStatus(): GrantStatus {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        return when (status) {
            AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.DENIED_ALWAYS
        }
    }

    private suspend fun requestCameraGrant(): GrantStatus = suspendCoroutine { continuation ->
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            continuation.resume(if (granted) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
        }
    }

    private fun checkPhotoStatus(): GrantStatus {
        val status = PHPhotoLibrary.authorizationStatus()
        return when (status) {
            PHAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            PHAuthorizationStatusLimited -> GrantStatus.PARTIAL_GRANTED
            PHAuthorizationStatusDenied, PHAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            PHAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.DENIED_ALWAYS
        }
    }

    private suspend fun requestPhotoGrant(): GrantStatus = suspendCoroutine { continuation ->
        PHPhotoLibrary.requestAuthorization { status ->
            continuation.resume(when (status) {
                PHAuthorizationStatusAuthorized -> GrantStatus.GRANTED
                PHAuthorizationStatusLimited -> GrantStatus.PARTIAL_GRANTED
                else -> GrantStatus.DENIED_ALWAYS
            })
        }
    }

    private fun checkLocationStatus(grant: AppGrant): GrantStatus {
        val status = CLLocationManager.authorizationStatus()
        return when (status) {
            kCLAuthorizationStatusAuthorizedAlways -> GrantStatus.GRANTED
            kCLAuthorizationStatusAuthorizedWhenInUse -> if (grant == AppGrant.LOCATION_ALWAYS) GrantStatus.PARTIAL_GRANTED else GrantStatus.GRANTED
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            kCLAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.DENIED_ALWAYS
        }
    }

    private suspend fun requestLocationGrant(grant: AppGrant): GrantStatus = suspendCoroutine { continuation ->
        // Simplified for brevity - needs CLLocationManagerDelegate implementation
        continuation.resume(GrantStatus.DENIED) 
    }

    private fun checkMicrophoneStatus(): GrantStatus {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)
        return when (status) {
            AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.DENIED_ALWAYS
        }
    }

    private suspend fun requestMicrophoneGrant(): GrantStatus = suspendCoroutine { continuation ->
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
            continuation.resume(if (granted) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
        }
    }

    private fun checkNotificationStatus(): GrantStatus {
        // UNUserNotificationCenter check is async, usually requires a workaround or returning cached
        return GrantStatus.NOT_DETERMINED
    }

    private suspend fun requestNotificationGrant(): GrantStatus = suspendCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound) { granted, _ ->
            continuation.resume(if (granted) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
        }
    }

    private fun checkBluetoothStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
    private suspend fun requestBluetoothGrant(): GrantStatus = GrantStatus.DENIED
    private fun checkMotionStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
    private suspend fun requestMotionGrant(): GrantStatus = GrantStatus.DENIED
    private fun checkContactsStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
    private suspend fun requestContactsStatus(): GrantStatus = GrantStatus.DENIED
    private fun checkCalendarStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
    private suspend fun requestCalendarStatus(): GrantStatus = GrantStatus.DENIED
}
