package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.mainContinuation
import dev.brewkits.grant.utils.mainContinuation2
import dev.brewkits.grant.utils.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.*
import platform.Contacts.*
import platform.CoreLocation.*
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Photos.*
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.*
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * iOS implementation of PlatformGrantDelegate.
 *
 * This class acts as a central hub for handling grant requests on iOS.
 * It uses specialized delegates for complex grants (Location, Bluetooth)
 * and direct framework calls for others (Camera, Microphone, Photos, etc.).
 *
 * Key features:
 * - Full support for all AppGrant types including MOTION and BLUETOOTH.
 * - Async/Await pattern using Kotlin Coroutines.
 * - Thread-safe callbacks ensuring execution on the Main Thread.
 */
actual class PlatformGrantDelegate {

    // Lazy initialization of delegates to avoid unnecessary resource allocation
    private val locationDelegate by lazy { LocationManagerDelegate() }
    private val bluetoothDelegate by lazy { BluetoothManagerDelegate() }
    private val motionManager by lazy { CMMotionActivityManager() }

    private val requestMutex = Mutex()

    actual suspend fun checkStatus(grant: AppGrant): GrantStatus {
        return when (grant) {
            AppGrant.CAMERA -> checkAVStatus(AVMediaTypeVideo)
            AppGrant.MICROPHONE -> checkAVStatus(AVMediaTypeAudio)
            AppGrant.GALLERY -> checkPhotoStatus()
            AppGrant.STORAGE -> checkPhotoStatus() // iOS treats Storage as Photos access
            AppGrant.LOCATION -> checkLocationStatus(always = false)
            AppGrant.LOCATION_ALWAYS -> checkLocationStatus(always = true)
            AppGrant.CONTACTS -> checkContactsStatus()
            AppGrant.NOTIFICATION -> checkNotificationStatus()
            AppGrant.BLUETOOTH -> bluetoothDelegate.checkStatus()
            AppGrant.MOTION -> checkMotionStatus()
        }
    }

    actual suspend fun request(grant: AppGrant): GrantStatus = requestMutex.withLock {
        return@withLock runOnMain {
            when (grant) {
                AppGrant.CAMERA -> requestAVGrant(AVMediaTypeVideo)
                AppGrant.MICROPHONE -> requestAVGrant(AVMediaTypeAudio)
                AppGrant.GALLERY -> requestPhotoGrant()
                AppGrant.STORAGE -> requestPhotoGrant()
                AppGrant.LOCATION -> requestLocationGrant(always = false)
                AppGrant.LOCATION_ALWAYS -> requestLocationGrant(always = true)
                AppGrant.CONTACTS -> requestContactsGrant()
                AppGrant.NOTIFICATION -> requestNotificationGrant()
                AppGrant.BLUETOOTH -> try {
                    bluetoothDelegate.requestBluetoothAccess()
                } catch (e: Exception) {
                    // Log warning
                    GrantLogger.w("iOSGrant", "Bluetooth request failed: ${e.message}")
                    // return DENIED instead of Crash
                    GrantStatus.DENIED_ALWAYS
                }

                AppGrant.MOTION -> requestMotionGrant()
            }
        }
    }

    actual fun openSettings() {
        try {
            val settingsUrl = NSURL(string = UIApplicationOpenSettingsURLString)
            if (UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
                UIApplication.sharedApplication.openURL(settingsUrl)
                GrantLogger.i("iOSGrant", "Opened app settings")
            } else {
                GrantLogger.e("iOSGrant", "Cannot open settings URL")
            }
        } catch (e: Exception) {
            GrantLogger.e("iOSGrant", "Failed to open settings", e)
        }
    }

    // =========================================================================
    // Private Helper Methods for Specific Grants
    // =========================================================================

    // --- AVFoundation (Camera & Microphone) ---

    private fun checkAVStatus(mediaType: AVMediaType): GrantStatus {
        return when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    private suspend fun requestAVGrant(mediaType: AVMediaType): GrantStatus {
        val currentStatus = checkAVStatus(mediaType)
        if (currentStatus != GrantStatus.NOT_DETERMINED) {
            return currentStatus
        }

        return suspendCancellableCoroutine { continuation ->
            CoroutineScope(Dispatchers.Main).launch {
                AVCaptureDevice.requestAccessForMediaType(
                    mediaType = mediaType,
                    completionHandler = mainContinuation { granted ->
                        val status = if (granted) {
                            GrantStatus.GRANTED
                        } else {
                            GrantStatus.DENIED_ALWAYS
                        }
                        continuation.resume(status)
                    }
                )
            }
        }
    }

    // --- Photos (Gallery & Storage) ---

    private fun checkPhotoStatus(): GrantStatus {
        return when (PHPhotoLibrary.authorizationStatus()) {
            PHAuthorizationStatusAuthorized,
            3L /* PHAuthorizationStatusLimited */ -> GrantStatus.GRANTED
            PHAuthorizationStatusDenied,
            PHAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            PHAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    private suspend fun requestPhotoGrant(): GrantStatus {
        val currentStatus = checkPhotoStatus()
        if (currentStatus != GrantStatus.NOT_DETERMINED) {
            return currentStatus
        }

        return suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorization { status ->
                // Ensure callback is handled on Main Thread if needed, though status mapping is safe
                val GrantStatus = when (status) {
                    PHAuthorizationStatusAuthorized,
                    3L /* PHAuthorizationStatusLimited */ -> GrantStatus.GRANTED
                    else -> GrantStatus.DENIED_ALWAYS
                }
                continuation.resume(GrantStatus)
            }
        }
    }

    // --- Contacts ---

    private fun checkContactsStatus(): GrantStatus {
        return when (CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)) {
            CNAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            CNAuthorizationStatusDenied,
            CNAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            CNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    private suspend fun requestContactsGrant(): GrantStatus {
        val currentStatus = checkContactsStatus()
        if (currentStatus != GrantStatus.NOT_DETERMINED) {
            return currentStatus
        }

        return suspendCancellableCoroutine { continuation ->
            val store = CNContactStore()
            store.requestAccessForEntityType(
                entityType = CNEntityType.CNEntityTypeContacts,
                completionHandler = mainContinuation2 { granted, _ ->
                    val status = if (granted) {
                        GrantStatus.GRANTED
                    } else {
                        GrantStatus.DENIED_ALWAYS
                    }
                    continuation.resume(status)
                }
            )
        }
    }

    // --- Location ---

    private fun checkLocationStatus(always: Boolean): GrantStatus {
        // We use the static method for synchronous checking
        val status = CLLocationManager.authorizationStatus()
        return mapLocationStatus(status, always)
    }

    private suspend fun requestLocationGrant(always: Boolean): GrantStatus {
        // Delegate handles the complex delegate callbacks and thread switching
        val authStatus = if (always) {
            locationDelegate.requestAlwaysAuthorization()
        } else {
            locationDelegate.requestWhenInUseAuthorization()
        }
        return mapLocationStatus(authStatus, always)
    }

    private fun mapLocationStatus(status: CLAuthorizationStatus, always: Boolean): GrantStatus {
        return when (status) {
            kCLAuthorizationStatusAuthorizedAlways -> GrantStatus.GRANTED
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                if (always) {
                    // User granted WhenInUse, but we requested Always.
                    // This is effectively a soft denial for the "Always" feature.
                    GrantStatus.DENIED
                } else {
                    GrantStatus.GRANTED
                }
            }
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            kCLAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Notifications ---

    private suspend fun checkNotificationStatus(): GrantStatus {
        return suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler(
                mainContinuation { settings ->
                    val status = when (settings?.authorizationStatus) {
                        UNAuthorizationStatusAuthorized,
                        UNAuthorizationStatusProvisional,
                        UNAuthorizationStatusEphemeral -> GrantStatus.GRANTED
                        UNAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
                        else -> GrantStatus.NOT_DETERMINED
                    }
                    continuation.resume(status)
                }
            )
        }
    }

    private suspend fun requestNotificationGrant(): GrantStatus {
        val currentStatus = checkNotificationStatus()
        if (currentStatus != GrantStatus.NOT_DETERMINED) {
            return currentStatus
        }

        return suspendCancellableCoroutine { continuation ->
            val options = UNAuthorizationOptionAlert or
                    UNAuthorizationOptionSound or
                    UNAuthorizationOptionBadge

            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(
                    options = options,
                    completionHandler = mainContinuation2 { granted, _ ->
                        val status = if (granted) {
                            GrantStatus.GRANTED
                        } else {
                            GrantStatus.DENIED_ALWAYS
                        }
                        continuation.resume(status)
                    }
                )
        }
    }

    // --- Motion / Activity Recognition ---

    private fun checkMotionStatus(): GrantStatus {
        // CMMotionActivityManager authorization status
        return when (CMMotionActivityManager.authorizationStatus()) {
            CMAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            CMAuthorizationStatusDenied,
            CMAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            CMAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    private suspend fun requestMotionGrant(): GrantStatus {
        val currentStatus = checkMotionStatus()
        if (currentStatus != GrantStatus.NOT_DETERMINED) {
            return currentStatus
        }

        return suspendCancellableCoroutine { continuation ->
            val now = NSDate()
            // Trigger grant dialog by querying activity for the current moment.
            // iOS doesn't have an explicit request method for Motion.
            motionManager.queryActivityStartingFromDate(
                start = now,
                toDate = now,
                toQueue = NSOperationQueue.mainQueue
            ) { _, error ->
                // After the query returns (user responded to dialog), check status again.
                // We ignore the actual activity data or error here.
                val newStatus = checkMotionStatus()
                continuation.resume(newStatus)
            }
        }
    }
}