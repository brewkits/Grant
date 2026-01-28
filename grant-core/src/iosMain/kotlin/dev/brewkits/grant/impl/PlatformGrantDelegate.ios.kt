package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.BluetoothPoweredOffException
import dev.brewkits.grant.delegates.BluetoothTimeoutException
import dev.brewkits.grant.delegates.BluetoothInitializationException
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.SimulatorDetector
import dev.brewkits.grant.utils.mainContinuation
import dev.brewkits.grant.utils.mainContinuation2
import dev.brewkits.grant.utils.runOnMain
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.*
import platform.Contacts.*
import platform.CoreLocation.*
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSBundle
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
 *
 * Note: iOS has perfect 4-state differentiation from OS APIs, so GrantStore is accepted
 * for consistency but not currently used (iOS doesn't need "requested before" tracking).
 */
actual class PlatformGrantDelegate(
    @Suppress("UNUSED_PARAMETER") private val store: GrantStore
) {

    // Lazy initialization of delegates to avoid unnecessary resource allocation
    private val locationDelegate by lazy { LocationManagerDelegate() }
    private val bluetoothDelegate by lazy { BluetoothManagerDelegate() }
    private val motionManager by lazy { CMMotionActivityManager() }

    private val requestMutex = Mutex()

    /**
     * Validates that the required Info.plist key exists before requesting permission.
     *
     * **Why This is Critical:**
     * - iOS requires specific Info.plist keys for each permission type
     * - Missing keys cause immediate SIGABRT crash (app terminates)
     * - Crash happens BEFORE user sees permission dialog
     * - No way to catch or recover from the crash
     *
     * **This validation provides:**
     * - Runtime safety check before calling native APIs
     * - Clear error message with the missing key and fix instructions
     * - Graceful degradation (returns DENIED_ALWAYS instead of crashing)
     * - Development-time feedback to fix Info.plist
     *
     * @param key The Info.plist key to check (e.g., "NSCameraUsageDescription")
     * @param grant The grant type for error messaging
     * @return true if key exists, false if missing
     */
    private fun validateInfoPlistKey(key: String, grant: AppGrant): Boolean {
        val bundle = NSBundle.mainBundle
        val value = bundle.objectForInfoDictionaryKey(key)

        if (value == null) {
            GrantLogger.e(
                "iOSGrant",
                """
                ⚠️ CRITICAL: Missing required Info.plist key for ${grant.name}

                Required key: $key

                Add this to your Info.plist file:
                <key>$key</key>
                <string>Describe why your app needs this permission</string>

                Without this key, your app will CRASH immediately with SIGABRT.

                For more information, see:
                https://developer.apple.com/documentation/bundleresources/information_property_list
                """.trimIndent()
            )
            return false
        }

        return true
    }

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        // Handle RawPermission (custom permissions) - Not yet fully implemented
        if (grant is RawPermission) {
            GrantLogger.w("iOSGrant", "RawPermission support not yet implemented: ${grant.identifier}")
            return GrantStatus.DENIED  // Conservative default
        }

        // Cast to AppGrant
        val appGrant = grant as AppGrant

        return when (appGrant) {
            AppGrant.CAMERA -> checkAVStatus(AVMediaTypeVideo)
            AppGrant.MICROPHONE -> checkAVStatus(AVMediaTypeAudio)
            AppGrant.GALLERY -> checkPhotoStatus()
            AppGrant.GALLERY_IMAGES_ONLY -> checkPhotoStatus() // iOS doesn't distinguish images vs videos
            AppGrant.GALLERY_VIDEO_ONLY -> checkPhotoStatus() // iOS doesn't distinguish images vs videos
            AppGrant.STORAGE -> checkPhotoStatus() // iOS treats Storage as Photos access
            AppGrant.LOCATION -> checkLocationStatus(always = false)
            AppGrant.LOCATION_ALWAYS -> checkLocationStatus(always = true)
            AppGrant.CONTACTS -> checkContactsStatus()
            AppGrant.NOTIFICATION -> checkNotificationStatus()
            AppGrant.BLUETOOTH -> bluetoothDelegate.checkStatus()
            AppGrant.MOTION -> checkMotionStatus()
            AppGrant.SCHEDULE_EXACT_ALARM -> GrantStatus.GRANTED // iOS: Always allowed, no permission needed
        }
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus = requestMutex.withLock {
        return@withLock runOnMain {
            // Handle RawPermission (custom permissions) - Not yet fully implemented
            if (grant is RawPermission) {
                GrantLogger.w("iOSGrant", "RawPermission support not yet implemented: ${grant.identifier}")
                return@runOnMain GrantStatus.DENIED  // Conservative default
            }

            // Cast to AppGrant
            val appGrant = grant as AppGrant

            when (appGrant) {
                AppGrant.CAMERA -> requestAVGrant(AVMediaTypeVideo)
                AppGrant.MICROPHONE -> requestAVGrant(AVMediaTypeAudio)
                AppGrant.GALLERY -> requestPhotoGrant()
                AppGrant.GALLERY_IMAGES_ONLY -> requestPhotoGrant() // iOS doesn't distinguish images vs videos
                AppGrant.GALLERY_VIDEO_ONLY -> requestPhotoGrant() // iOS doesn't distinguish images vs videos
                AppGrant.STORAGE -> requestPhotoGrant()
                AppGrant.LOCATION -> requestLocationGrant(always = false)
                AppGrant.LOCATION_ALWAYS -> requestLocationGrant(always = true)
                AppGrant.CONTACTS -> requestContactsGrant()
                AppGrant.NOTIFICATION -> requestNotificationGrant()
                AppGrant.BLUETOOTH -> try {
                    // PRE-CHECK: Validate Info.plist key before requesting (iOS 13+)
                    if (!validateInfoPlistKey("NSBluetoothAlwaysUsageDescription", AppGrant.BLUETOOTH)) {
                        return@withLock GrantStatus.DENIED_ALWAYS
                    }
                    bluetoothDelegate.requestBluetoothAccess()
                } catch (e: BluetoothTimeoutException) {
                    // Timeout - temporary error, user can retry
                    GrantLogger.w("iOSGrant", "Bluetooth request timed out: ${e.message}")
                    GrantStatus.DENIED // Soft denial - can retry
                } catch (e: BluetoothInitializationException) {
                    // Initialization failed - temporary error
                    GrantLogger.w("iOSGrant", "Bluetooth initialization failed: ${e.message}")
                    GrantStatus.DENIED // Soft denial - can retry
                } catch (e: BluetoothPoweredOffException) {
                    // Bluetooth is powered off - user needs to enable it
                    GrantLogger.w("iOSGrant", "Bluetooth is powered off: ${e.message}")
                    GrantStatus.DENIED // Soft denial - user can enable BT
                } catch (e: Exception) {
                    // Unknown error - be conservative, allow retry
                    GrantLogger.e("iOSGrant", "Bluetooth request failed with unknown error", e)
                    GrantStatus.DENIED // Soft denial - not permanent
                }

                AppGrant.MOTION -> requestMotionGrant()
                AppGrant.SCHEDULE_EXACT_ALARM -> {
                    // iOS: No permission needed for exact alarms, always granted
                    GrantLogger.i("iOSGrant", "SCHEDULE_EXACT_ALARM automatically granted on iOS")
                    GrantStatus.GRANTED
                }
            }
        }
    }

    actual fun openSettings() {
        try {
            val settingsUrl = NSURL(string = UIApplicationOpenSettingsURLString)
            if (settingsUrl != null && UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
                // Use modern API: openURL:options:completionHandler:
                // This replaces the deprecated openURL: method (deprecated since iOS 10)
                UIApplication.sharedApplication.openURL(
                    settingsUrl,
                    options = emptyMap<Any?, Any?>(),
                    completionHandler = { success ->
                        if (success) {
                            GrantLogger.i("iOSGrant", "Successfully opened app settings")
                        } else {
                            GrantLogger.e("iOSGrant", "Failed to open settings URL - system denied the request")
                        }
                    }
                )
            } else {
                if (settingsUrl == null) {
                    GrantLogger.e("iOSGrant", "Cannot create settings URL from UIApplicationOpenSettingsURLString")
                } else {
                    GrantLogger.e("iOSGrant", "Cannot open settings URL - canOpenURL returned false")
                }
            }
        } catch (e: Exception) {
            GrantLogger.e("iOSGrant", "Exception while opening settings: ${e.message}", e)
        }
    }

    // =========================================================================
    // Private Helper Methods for Specific Grants
    // =========================================================================

    // --- AVFoundation (Camera & Microphone) ---

    private fun checkAVStatus(mediaType: AVMediaType): GrantStatus {
        try {
            return when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
                AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
                AVAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
                AVAuthorizationStatusRestricted -> {
                    GrantLogger.w("iOSGrant", "AV permission restricted (may be parental controls)")
                    GrantStatus.DENIED_ALWAYS
                }
                AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
                else -> {
                    GrantLogger.w("iOSGrant", "Unknown AV authorization status, returning NOT_DETERMINED")
                    GrantStatus.NOT_DETERMINED
                }
            }
        } catch (e: Exception) {
            GrantLogger.e("iOSGrant", "Error checking AV authorization status: ${e.message}", e)
            return GrantStatus.NOT_DETERMINED
        }
    }

    private suspend fun requestAVGrant(mediaType: AVMediaType): GrantStatus {
        val (mediaTypeName, infoKey, grant) = when (mediaType) {
            AVMediaTypeVideo -> Triple("Camera", "NSCameraUsageDescription", AppGrant.CAMERA)
            AVMediaTypeAudio -> Triple("Microphone", "NSMicrophoneUsageDescription", AppGrant.MICROPHONE)
            else -> Triple("AV", "NSCameraUsageDescription", AppGrant.CAMERA)
        }

        // PRE-CHECK: Validate Info.plist key before requesting
        // This prevents SIGABRT crash if the key is missing
        if (!validateInfoPlistKey(infoKey, grant)) {
            return GrantStatus.DENIED_ALWAYS  // Safer than crashing
        }

        val currentStatus = checkAVStatus(mediaType)
        if (currentStatus != GrantStatus.NOT_DETERMINED) {
            GrantLogger.i("iOSGrant", "$mediaTypeName already determined: $currentStatus")
            return currentStatus
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                GrantLogger.i("iOSGrant", "Requesting $mediaTypeName access...")
                // Direct call - no CoroutineScope.launch wrapper needed
                // We're already guaranteed to be on main thread by runOnMain() in request()
                // Wrapping in launch causes deadlock (moko-permissions issue #129)
                AVCaptureDevice.requestAccessForMediaType(
                    mediaType = mediaType,
                    completionHandler = mainContinuation { granted ->
                        val status = if (granted) {
                            GrantLogger.i("iOSGrant", "$mediaTypeName access granted")
                            GrantStatus.GRANTED
                        } else {
                            GrantLogger.i("iOSGrant", "$mediaTypeName access denied")
                            GrantStatus.DENIED_ALWAYS
                        }
                        continuation.resume(status)
                    }
                )
            } catch (e: Exception) {
                GrantLogger.e("iOSGrant", "Error requesting $mediaTypeName access: ${e.message}", e)
                continuation.resume(GrantStatus.DENIED)
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
        // PRE-CHECK: Validate Info.plist key before requesting
        if (!validateInfoPlistKey("NSPhotoLibraryUsageDescription", AppGrant.GALLERY)) {
            return GrantStatus.DENIED_ALWAYS
        }

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
        // PRE-CHECK: Validate Info.plist key before requesting
        if (!validateInfoPlistKey("NSContactsUsageDescription", AppGrant.CONTACTS)) {
            return GrantStatus.DENIED_ALWAYS
        }

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
        // PRE-CHECK: Validate Info.plist keys before requesting
        // iOS 11+ requires both keys for "always" authorization
        if (always) {
            // "Always" needs both keys
            val hasWhenInUse = validateInfoPlistKey("NSLocationWhenInUseUsageDescription", AppGrant.LOCATION_ALWAYS)
            val hasAlways = validateInfoPlistKey("NSLocationAlwaysAndWhenInUseUsageDescription", AppGrant.LOCATION_ALWAYS)
            if (!hasWhenInUse || !hasAlways) {
                return GrantStatus.DENIED_ALWAYS
            }
        } else {
            // "When in use" needs only WhenInUse key
            if (!validateInfoPlistKey("NSLocationWhenInUseUsageDescription", AppGrant.LOCATION)) {
                return GrantStatus.DENIED_ALWAYS
            }
        }

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
        // iOS Simulator: Motion works but may not return real data
        // Return GRANTED to allow testing without blocking
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(
                "iOSGrant",
                "Running on ${SimulatorDetector.simulatorType} - Motion permission dialog works, returning GRANTED"
            )
            return GrantStatus.GRANTED
        }

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
        // iOS Simulator: Motion dialog works but data collection may not work
        // Return GRANTED to allow testing
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(
                "iOSGrant",
                "Running on ${SimulatorDetector.simulatorType} - Returning GRANTED for Motion (data collection may not work)"
            )
            return GrantStatus.GRANTED
        }

        // PRE-CHECK: Validate Info.plist key before requesting
        if (!validateInfoPlistKey("NSMotionUsageDescription", AppGrant.MOTION)) {
            return GrantStatus.DENIED_ALWAYS
        }

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