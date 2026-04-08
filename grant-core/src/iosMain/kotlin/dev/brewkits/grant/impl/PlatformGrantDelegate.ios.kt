package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.BluetoothPoweredOffException
import dev.brewkits.grant.delegates.BluetoothTimeoutException
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.SimulatorDetector
import dev.brewkits.grant.utils.mainContinuation
import dev.brewkits.grant.utils.runOnMain
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorized
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusNotDetermined
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMMotionActivityManager
import platform.EventKit.EKAuthorizationStatus
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.Foundation.NSURL
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "iOSGrantDelegate"

/**
 * iOS Platform Grant Delegate — Production-ready implementation.
 *
 * Provides native iOS permission handling for all 17 supported grant types.
 * Each permission type uses the appropriate Apple framework API:
 * - Camera/Microphone: AVFoundation
 * - Gallery/Storage: Photos Framework
 * - Location: CoreLocation (delegate-based)
 * - Notification: UserNotifications
 * - Contacts: Contacts Framework
 * - Calendar: EventKit
 * - Motion: CoreMotion (dummy query pattern)
 * - Bluetooth: CoreBluetooth (delegate-based)
 *
 * **Thread Safety**: All iOS framework calls are dispatched to the main thread
 * via [runOnMain] or [mainContinuation].
 *
 * **Info.plist Validation**: Validates required usage description keys before
 * calling native APIs to prevent SIGABRT crashes.
 */
actual class PlatformGrantDelegate(
    private val store: GrantStore
) {
    // --- Concurrency ---
    private val mutexMap = mutableMapOf<String, Mutex>()
    private fun getMutexFor(identifier: String): Mutex = mutexMap.getOrPut(identifier) { Mutex() }

    // --- Status Cache (Monotonic Time) ---
    private val statusCacheMap = mutableMapOf<String, Pair<GrantStatus, Long>>()
    private companion object { const val STATUS_CACHE_TTL_MS = 1000L }
    private fun getMonotonicTimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    // --- Delegates for async framework callbacks ---
    private val locationDelegate by lazy { LocationManagerDelegate() }
    private val bluetoothDelegate by lazy { BluetoothManagerDelegate() }

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
            if (getMonotonicTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) return cachedStatus
        }
        val status = checkStatusInternal(grant)
        statusCacheMap[identifier] = status to getMonotonicTimeMillis()
        return status
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus =
        getMutexFor(grant.identifier).withLock { requestInternal(grant) }

    actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        val results = mutableMapOf<GrantPermission, GrantStatus>()
        // iOS requests permissions sequentially (native behavior)
        grants.forEach { results[it] = request(it) }
        return results
    }

    actual fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url != null) {
            UIApplication.sharedApplication.openURL(
                url,
                options = emptyMap<Any?, Any>(),
                completionHandler = null
            )
        }
    }

    // ====================================================================
    // STATUS CHECKS
    // ====================================================================

    private suspend fun checkStatusInternal(grant: GrantPermission): GrantStatus = runOnMain {
        if (grant is RawPermission) {
            // For RawPermission on iOS, validate Info.plist key
            val iosKey = grant.iosUsageKey
            if (iosKey != null && !hasInfoPlistKey(iosKey)) {
                GrantLogger.e(TAG, "Missing '$iosKey' in Info.plist for RawPermission '${grant.identifier}'")
                return@runOnMain GrantStatus.DENIED_ALWAYS
            }
            return@runOnMain GrantStatus.NOT_DETERMINED
        }

        when (grant as AppGrant) {
            AppGrant.CAMERA -> checkAVStatus(AVMediaTypeVideo!!, "NSCameraUsageDescription")
            AppGrant.MICROPHONE -> checkAVStatus(AVMediaTypeAudio!!, "NSMicrophoneUsageDescription")

            AppGrant.GALLERY,
            AppGrant.STORAGE,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY -> checkPhotoStatus()

            AppGrant.LOCATION -> checkLocationStatus(forAlways = false)
            AppGrant.LOCATION_ALWAYS -> checkLocationStatus(forAlways = true)

            AppGrant.NOTIFICATION -> checkNotificationStatus()

            AppGrant.CONTACTS,
            AppGrant.READ_CONTACTS -> checkContactsStatus()

            AppGrant.CALENDAR,
            AppGrant.READ_CALENDAR -> checkCalendarStatus()

            AppGrant.MOTION -> checkMotionStatus()

            AppGrant.BLUETOOTH,
            AppGrant.BLUETOOTH_ADVERTISE -> checkBluetoothStatus()

            AppGrant.SCHEDULE_EXACT_ALARM -> GrantStatus.GRANTED // iOS always allows alarms
        }
    }

    // --- AVFoundation (Camera / Microphone) ---
    private fun checkAVStatus(mediaType: String, plistKey: String): GrantStatus {
        if (!hasInfoPlistKey(plistKey)) {
            GrantLogger.e(TAG, "Missing '$plistKey' in Info.plist. Returning DENIED_ALWAYS.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Photos ---
    internal fun checkPhotoStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSPhotoLibraryUsageDescription")) {
            GrantLogger.e(TAG, "Missing 'NSPhotoLibraryUsageDescription' in Info.plist.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (PHPhotoLibrary.authorizationStatus()) {
            PHAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            PHAuthorizationStatusLimited -> GrantStatus.PARTIAL_GRANTED
            PHAuthorizationStatusDenied, PHAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            PHAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Location ---
    private fun checkLocationStatus(forAlways: Boolean): GrantStatus {
        val requiredKey = if (forAlways) "NSLocationAlwaysAndWhenInUseUsageDescription"
            else "NSLocationWhenInUseUsageDescription"
        if (!hasInfoPlistKey(requiredKey)) {
            GrantLogger.e(TAG, "Missing '$requiredKey' in Info.plist.")
            return GrantStatus.DENIED_ALWAYS
        }

        val status = CLLocationManager.authorizationStatus()
        return when (status) {
            kCLAuthorizationStatusAuthorizedAlways -> GrantStatus.GRANTED
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                if (forAlways) GrantStatus.PARTIAL_GRANTED else GrantStatus.GRANTED
            }
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            kCLAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Notifications ---
    private suspend fun checkNotificationStatus(): GrantStatus {
        return suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    val status = settings?.authorizationStatus
                    val result = when (status) {
                        UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> GrantStatus.GRANTED
                        UNAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
                        UNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
                        else -> GrantStatus.NOT_DETERMINED
                    }
                    mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
                }
        }
    }

    // --- Contacts ---
    private fun checkContactsStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSContactsUsageDescription")) {
            GrantLogger.e(TAG, "Missing 'NSContactsUsageDescription' in Info.plist.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)) {
            CNAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            CNAuthorizationStatusDenied, CNAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            CNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Calendar ---
    private fun checkCalendarStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSCalendarsUsageDescription") &&
            !hasInfoPlistKey("NSCalendarsFullAccessUsageDescription")) {
            GrantLogger.e(TAG, "Missing calendar usage description in Info.plist.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)) {
            EKAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            EKAuthorizationStatusDenied, EKAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            EKAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> {
                // iOS 17+: .fullAccess maps to GRANTED, .writeOnly maps to PARTIAL_GRANTED
                // The raw Long value for these new statuses:
                // EKAuthorizationStatusFullAccess = 3, EKAuthorizationStatusWriteOnly = 4
                val rawStatus = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
                when (rawStatus) {
                    3L -> GrantStatus.GRANTED           // fullAccess
                    4L -> GrantStatus.PARTIAL_GRANTED    // writeOnly
                    else -> GrantStatus.NOT_DETERMINED
                }
            }
        }
    }

    // --- Motion ---
    private fun checkMotionStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSMotionUsageDescription")) {
            GrantLogger.e(TAG, "Missing 'NSMotionUsageDescription' in Info.plist.")
            return GrantStatus.DENIED_ALWAYS
        }
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(TAG, "Simulator detected — returning GRANTED for Motion.")
            return GrantStatus.GRANTED
        }
        if (!CMMotionActivityManager.isActivityAvailable()) {
            GrantLogger.w(TAG, "Motion activity is not available on this device.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (CMMotionActivityManager.authorizationStatus()) {
            CMAuthorizationStatusAuthorized -> GrantStatus.GRANTED
            CMAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
            CMAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            CMAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Bluetooth ---
    private fun checkBluetoothStatus(): GrantStatus {
        return bluetoothDelegate.checkStatus()
    }

    // ====================================================================
    // REQUEST LOGIC
    // ====================================================================

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        // Invalidate cache before request
        statusCacheMap.remove(grant.identifier)

        val currentStatus = checkStatus(grant)
        if (currentStatus == GrantStatus.GRANTED || currentStatus == GrantStatus.PARTIAL_GRANTED) {
            return currentStatus
        }
        if (currentStatus == GrantStatus.DENIED_ALWAYS) {
            GrantLogger.w(TAG, "Grant '${grant.identifier}' is permanently denied. User must enable in Settings.")
            return currentStatus
        }

        if (grant is RawPermission) {
            GrantLogger.w(TAG, "RawPermission '${grant.identifier}' on iOS requires native handling.")
            return GrantStatus.DENIED
        }

        val result = when (grant as AppGrant) {
            AppGrant.CAMERA -> requestAVAccess(AVMediaTypeVideo!!, "NSCameraUsageDescription")
            AppGrant.MICROPHONE -> requestAVAccess(AVMediaTypeAudio!!, "NSMicrophoneUsageDescription")

            AppGrant.GALLERY,
            AppGrant.STORAGE,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY -> requestPhotoAccess()

            AppGrant.LOCATION -> requestLocationAccess(forAlways = false)
            AppGrant.LOCATION_ALWAYS -> requestLocationAccess(forAlways = true)

            AppGrant.NOTIFICATION -> requestNotificationAccess()

            AppGrant.CONTACTS,
            AppGrant.READ_CONTACTS -> requestContactsAccess()

            AppGrant.CALENDAR,
            AppGrant.READ_CALENDAR -> requestCalendarAccess()

            AppGrant.MOTION -> requestMotionAccess()

            AppGrant.BLUETOOTH,
            AppGrant.BLUETOOTH_ADVERTISE -> requestBluetoothAccess()

            AppGrant.SCHEDULE_EXACT_ALARM -> GrantStatus.GRANTED
        }

        // Invalidate cache after request
        statusCacheMap.remove(grant.identifier)
        return result
    }

    // --- AVFoundation Request (Camera / Microphone) ---
    private suspend fun requestAVAccess(mediaType: String, plistKey: String): GrantStatus {
        if (!hasInfoPlistKey(plistKey)) {
            GrantLogger.e(TAG, "Missing '$plistKey' in Info.plist. Cannot request.")
            return GrantStatus.DENIED_ALWAYS
        }
        return suspendCancellableCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
                mainContinuation<Boolean> { g ->
                    cont.resume(if (g) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
                }.invoke(granted)
            }
        }
    }

    // --- Photos Request ---
    private suspend fun requestPhotoAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSPhotoLibraryUsageDescription")) {
            return GrantStatus.DENIED_ALWAYS
        }
        return suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.requestAuthorization { status ->
                val result = when (status) {
                    PHAuthorizationStatusAuthorized -> GrantStatus.GRANTED
                    PHAuthorizationStatusLimited -> GrantStatus.PARTIAL_GRANTED
                    PHAuthorizationStatusDenied, PHAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
                    else -> GrantStatus.DENIED
                }
                mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
            }
        }
    }

    // --- Location Request (delegate-based) ---
    private suspend fun requestLocationAccess(forAlways: Boolean): GrantStatus {
        val requiredKey = if (forAlways) "NSLocationAlwaysAndWhenInUseUsageDescription"
            else "NSLocationWhenInUseUsageDescription"
        if (!hasInfoPlistKey(requiredKey)) {
            return GrantStatus.DENIED_ALWAYS
        }

        val clStatus: CLAuthorizationStatus = if (forAlways) {
            locationDelegate.requestAlwaysAuthorization()
        } else {
            locationDelegate.requestWhenInUseAuthorization()
        }

        return when (clStatus) {
            kCLAuthorizationStatusAuthorizedAlways -> GrantStatus.GRANTED
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                if (forAlways) GrantStatus.PARTIAL_GRANTED else GrantStatus.GRANTED
            }
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
            else -> GrantStatus.DENIED
        }
    }

    // --- Notification Request ---
    private suspend fun requestNotificationAccess(): GrantStatus {
        return suspendCancellableCoroutine { cont ->
            val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(options) { granted, error ->
                    if (error != null) {
                        GrantLogger.e(TAG, "Notification request error: ${error.localizedDescription}")
                    }
                    mainContinuation<Boolean> { g ->
                        cont.resume(if (g) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
                    }.invoke(granted)
                }
        }
    }

    // --- Contacts Request ---
    private suspend fun requestContactsAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSContactsUsageDescription")) {
            return GrantStatus.DENIED_ALWAYS
        }
        return suspendCancellableCoroutine { cont ->
            CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { granted, error ->
                if (error != null) {
                    GrantLogger.e(TAG, "Contacts request error: ${error.localizedDescription}")
                }
                mainContinuation<Boolean> { g ->
                    cont.resume(if (g) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
                }.invoke(granted)
            }
        }
    }

    // --- Calendar Request (iOS 17+ aware) ---
    private suspend fun requestCalendarAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSCalendarsUsageDescription") &&
            !hasInfoPlistKey("NSCalendarsFullAccessUsageDescription")) {
            return GrantStatus.DENIED_ALWAYS
        }
        return suspendCancellableCoroutine { cont ->
            val eventStore = EKEventStore()
            // Use legacy API which works on all iOS versions
            // On iOS 17+, this still works if NSCalendarsFullAccessUsageDescription is present
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, error ->
                if (error != null) {
                    GrantLogger.e(TAG, "Calendar request error: ${error.localizedDescription}")
                }
                val result = if (granted) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS
                mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
            }
        }
    }

    // --- Motion Request (Dummy Query — Industry Standard) ---
    private suspend fun requestMotionAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSMotionUsageDescription")) {
            return GrantStatus.DENIED_ALWAYS
        }
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(TAG, "Simulator detected — returning GRANTED for Motion request.")
            return GrantStatus.GRANTED
        }
        if (!CMMotionActivityManager.isActivityAvailable()) {
            return GrantStatus.DENIED_ALWAYS
        }

        // Dummy Query pattern — triggers the system permission dialog
        // Same approach used by MOKO-permissions and Flutter permission_handler
        return suspendCancellableCoroutine { cont ->
            val activityManager = CMMotionActivityManager()
            val now = NSDate()
            activityManager.queryActivityStartingFromDate(
                start = now,
                toDate = now,
                toQueue = NSOperationQueue.mainQueue
            ) { _, error ->
                val status = CMMotionActivityManager.authorizationStatus()
                val result = when (status) {
                    CMAuthorizationStatusAuthorized -> GrantStatus.GRANTED
                    CMAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
                    CMAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
                    else -> GrantStatus.DENIED
                }
                mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
            }
        }
    }

    // --- Bluetooth Request (delegate-based) ---
    private suspend fun requestBluetoothAccess(): GrantStatus {
        return try {
            bluetoothDelegate.requestBluetoothAccess()
        } catch (e: BluetoothTimeoutException) {
            GrantLogger.w(TAG, "Bluetooth request timed out: ${e.message}")
            GrantStatus.DENIED
        } catch (e: BluetoothPoweredOffException) {
            GrantLogger.w(TAG, "Bluetooth is powered off: ${e.message}")
            GrantStatus.DENIED
        } catch (e: Exception) {
            GrantLogger.e(TAG, "Bluetooth request failed: ${e.message}", e)
            GrantStatus.DENIED
        }
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    /**
     * Validates that a required Info.plist key is present.
     * Missing keys cause SIGABRT crashes when calling native APIs.
     */
    private fun hasInfoPlistKey(key: String): Boolean {
        val value = NSBundle.mainBundle.objectForInfoDictionaryKey(key)
        if (value == null) {
            GrantLogger.e(
                TAG,
                "MISSING Info.plist key: '$key'. " +
                "Add this key to your Info.plist to prevent crashes. " +
                "Returning DENIED_ALWAYS as a safety fallback."
            )
            return false
        }
        return true
    }
}
