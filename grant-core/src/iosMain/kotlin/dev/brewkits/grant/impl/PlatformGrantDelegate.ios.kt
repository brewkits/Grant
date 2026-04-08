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
import platform.Photos.PHAccessLevelReadWrite
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
 * - Gallery/Storage: Photos Framework (PHPhotoLibrary)
 * - Location: CoreLocation (delegate-based)
 * - Notification: UserNotifications
 * - Contacts: Contacts Framework
 * - Calendar: EventKit (iOS 17+ writeOnly aware)
 * - Motion: CoreMotion (dummy query pattern)
 * - Bluetooth: CoreBluetooth (delegate-based)
 *
 * **Thread Safety**:
 * - `mutexMap` is protected by `mapsMutex` (C1 fix)
 * - `statusCacheMap` uses per-entry locking via `mapsMutex` (C2 fix)
 * - All iOS framework calls dispatch to main thread via [runOnMain] / [mainContinuation]
 *
 * **Info.plist Validation**: Validates required usage description keys before
 * calling native APIs to prevent SIGABRT crashes.
 */
actual class PlatformGrantDelegate(
    private val store: GrantStore
) {
    // ====================================================================
    // FIX C1 + C2: Thread-safe maps using a dedicated protecting Mutex.
    //
    // Previously `mutableMapOf()` was used directly — not thread-safe on
    // Kotlin/Native when accessed from multiple coroutines on different
    // threads. All map mutations now go through `mapsMutex.withLock`.
    // ====================================================================

    /** Protects all map structures (mutexMapInternal, statusCacheMap). */
    private val mapsMutex = Mutex()
    private val mutexMapInternal = mutableMapOf<String, Mutex>()
    private val statusCacheMap = mutableMapOf<String, Pair<GrantStatus, Long>>()

    private companion object {
        /** Status cache TTL (1 second). Prevents redundant OS calls within the same interaction. */
        const val STATUS_CACHE_TTL_MS = 1000L
    }

    // FIX C1: getMutexFor is now suspend and acquires mapsMutex before touching the internal map.
    private suspend fun getMutexFor(identifier: String): Mutex {
        return mapsMutex.withLock {
            mutexMapInternal.getOrPut(identifier) { Mutex() }
        }
    }

    private fun getMonotonicTimeMillis(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    // --- Delegates for async framework callbacks ---
    private val locationDelegate by lazy { LocationManagerDelegate() }
    private val bluetoothDelegate by lazy { BluetoothManagerDelegate() }

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        // FIX C2: Protect statusCacheMap reads/writes through mapsMutex.
        mapsMutex.withLock {
            statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
                if (getMonotonicTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) return cachedStatus
            }
        }
        val status = checkStatusInternal(grant)
        mapsMutex.withLock {
            statusCacheMap[identifier] = status to getMonotonicTimeMillis()
        }
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

    /**
     * FIX L4: Proper fallback when Settings URL scheme is unavailable
     * (App Extensions, App Clips). Logs a warning instead of silently failing.
     */
    actual fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url == null) {
            GrantLogger.w(TAG, "UIApplicationOpenSettingsURLString could not be resolved. Settings cannot be opened.")
            return
        }
        UIApplication.sharedApplication.openURL(
            url,
            options = emptyMap<Any?, Any>(),
            completionHandler = { success ->
                if (!success) {
                    GrantLogger.w(TAG, "openURL to Settings returned false — Settings may not be accessible in this context.")
                }
            }
        )
    }

    // ====================================================================
    // STATUS CHECKS
    // ====================================================================

    /**
     * FIX M2: NOTIFICATION check uses suspendCancellableCoroutine internally
     * and dispatches its own callback onto the main queue — wrapping it in
     * `runOnMain` caused nested dispatch_async which can deadlock when already
     * on the main thread. NOTIFICATION is therefore called outside the runOnMain block.
     */
    private suspend fun checkStatusInternal(grant: GrantPermission): GrantStatus {
        if (grant is RawPermission) {
            val iosKey = grant.iosUsageKey
            if (iosKey != null && !hasInfoPlistKey(iosKey)) {
                return GrantStatus.DENIED_ALWAYS
            }
            return GrantStatus.NOT_DETERMINED
        }

        // NOTIFICATION requires its own async path (avoid nested runOnMain)
        if ((grant as AppGrant) == AppGrant.NOTIFICATION) {
            return checkNotificationStatus()
        }

        return runOnMain {
            when (grant) {
                AppGrant.CAMERA     -> checkAVStatus(AVMediaTypeVideo!!, "NSCameraUsageDescription")
                AppGrant.MICROPHONE -> checkAVStatus(AVMediaTypeAudio!!, "NSMicrophoneUsageDescription")

                AppGrant.GALLERY,
                AppGrant.STORAGE,
                AppGrant.GALLERY_IMAGES_ONLY,
                AppGrant.GALLERY_VIDEO_ONLY -> checkPhotoStatus()

                AppGrant.LOCATION       -> checkLocationStatus(forAlways = false)
                AppGrant.LOCATION_ALWAYS -> checkLocationStatus(forAlways = true)

                AppGrant.NOTIFICATION -> checkNotificationStatus() // unreachable here; handled above

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
    }

    // --- AVFoundation (Camera / Microphone) ---
    private fun checkAVStatus(mediaType: String, plistKey: String): GrantStatus {
        if (!hasInfoPlistKey(plistKey)) return GrantStatus.DENIED_ALWAYS
        return when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized   -> GrantStatus.GRANTED
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted   -> GrantStatus.DENIED_ALWAYS
            AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Photos ---
    internal fun checkPhotoStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSPhotoLibraryUsageDescription")) return GrantStatus.DENIED_ALWAYS
        return when (PHPhotoLibrary.authorizationStatus()) {
            PHAuthorizationStatusAuthorized   -> GrantStatus.GRANTED
            PHAuthorizationStatusLimited      -> GrantStatus.PARTIAL_GRANTED
            PHAuthorizationStatusDenied,
            PHAuthorizationStatusRestricted   -> GrantStatus.DENIED_ALWAYS
            PHAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Location ---
    private fun checkLocationStatus(forAlways: Boolean): GrantStatus {
        val requiredKey = if (forAlways) "NSLocationAlwaysAndWhenInUseUsageDescription"
                          else "NSLocationWhenInUseUsageDescription"
        if (!hasInfoPlistKey(requiredKey)) return GrantStatus.DENIED_ALWAYS

        return when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways   -> GrantStatus.GRANTED
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                if (forAlways) GrantStatus.PARTIAL_GRANTED else GrantStatus.GRANTED
            }
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted  -> GrantStatus.DENIED_ALWAYS
            kCLAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Notifications ---
    private suspend fun checkNotificationStatus(): GrantStatus {
        return suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    val result = when (settings?.authorizationStatus) {
                        UNAuthorizationStatusAuthorized,
                        UNAuthorizationStatusProvisional -> GrantStatus.GRANTED
                        UNAuthorizationStatusDenied      -> GrantStatus.DENIED_ALWAYS
                        UNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
                        else -> GrantStatus.NOT_DETERMINED
                    }
                    mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
                }
        }
    }

    // --- Contacts ---
    private fun checkContactsStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSContactsUsageDescription")) return GrantStatus.DENIED_ALWAYS
        return when (CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)) {
            CNAuthorizationStatusAuthorized   -> GrantStatus.GRANTED
            CNAuthorizationStatusDenied,
            CNAuthorizationStatusRestricted   -> GrantStatus.DENIED_ALWAYS
            CNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Calendar ---
    /**
     * FIX L3: Store raw status in a single val before branching — prevents
     * calling `authorizationStatusForEntityType` twice (race-prone + wasteful).
     *
     * iOS 17+ mapping:
     * - EKAuthorizationStatusFullAccess (raw = 3) → GRANTED
     * - EKAuthorizationStatusWriteOnly  (raw = 4) → PARTIAL_GRANTED
     */
    internal fun checkCalendarStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSCalendarsUsageDescription") &&
            !hasInfoPlistKey("NSCalendarsFullAccessUsageDescription")) {
            return GrantStatus.DENIED_ALWAYS
        }
        val rawStatus: EKAuthorizationStatus =
            EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        return when (rawStatus) {
            EKAuthorizationStatusAuthorized   -> GrantStatus.GRANTED  // iOS < 17
            EKAuthorizationStatusDenied,
            EKAuthorizationStatusRestricted   -> GrantStatus.DENIED_ALWAYS
            EKAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> when (rawStatus) {
                // iOS 17+: EKAuthorizationStatusFullAccess = 3, EKAuthorizationStatusWriteOnly = 4
                3L -> GrantStatus.GRANTED
                4L -> GrantStatus.PARTIAL_GRANTED
                else -> GrantStatus.NOT_DETERMINED
            }
        }
    }

    // --- Motion ---
    private fun checkMotionStatus(): GrantStatus {
        if (!hasInfoPlistKey("NSMotionUsageDescription")) return GrantStatus.DENIED_ALWAYS
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(TAG, "Simulator detected — returning GRANTED for Motion checkStatus.")
            return GrantStatus.GRANTED
        }
        if (!CMMotionActivityManager.isActivityAvailable()) {
            GrantLogger.w(TAG, "Motion activity hardware not available on this device.")
            return GrantStatus.DENIED_ALWAYS
        }
        return when (CMMotionActivityManager.authorizationStatus()) {
            CMAuthorizationStatusAuthorized   -> GrantStatus.GRANTED
            CMAuthorizationStatusDenied,
            CMAuthorizationStatusRestricted   -> GrantStatus.DENIED_ALWAYS
            CMAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    // --- Bluetooth ---
    private fun checkBluetoothStatus(): GrantStatus = bluetoothDelegate.checkStatus()

    // ====================================================================
    // REQUEST LOGIC
    // ====================================================================

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        // Invalidate cache before request
        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }

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
            AppGrant.CAMERA     -> requestAVAccess(AVMediaTypeVideo!!, "NSCameraUsageDescription")
            AppGrant.MICROPHONE -> requestAVAccess(AVMediaTypeAudio!!, "NSMicrophoneUsageDescription")

            AppGrant.GALLERY,
            AppGrant.STORAGE,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY -> requestPhotoAccess()

            AppGrant.LOCATION       -> requestLocationAccess(forAlways = false)
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

        // Invalidate cache after request so next checkStatus reads fresh from OS
        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }
        return result
    }

    // --- AVFoundation Request (Camera / Microphone) ---
    private suspend fun requestAVAccess(mediaType: String, plistKey: String): GrantStatus {
        if (!hasInfoPlistKey(plistKey)) return GrantStatus.DENIED_ALWAYS
        return suspendCancellableCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(mediaType) { granted ->
                mainContinuation<Boolean> { g ->
                    cont.resume(if (g) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
                }.invoke(granted)
            }
        }
    }

    /**
     * FIX M4: Use non-deprecated `requestAuthorizationForAccessLevel(_:handler:)` on iOS 14+.
     * Falls back to legacy `requestAuthorization(_:)` on iOS < 14.
     *
     * iOS 14+: PHAccessLevel.readWrite requests full read+write access.
     * PHAuthorizationStatusLimited → PARTIAL_GRANTED (user selected specific photos).
     */
    private suspend fun requestPhotoAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSPhotoLibraryUsageDescription")) return GrantStatus.DENIED_ALWAYS
        // Check iOS 14+ for the non-deprecated PHPhotoLibrary access-level API.
        // NSProcessInfo.operatingSystemVersionString returns e.g. "Version 17.0 (Build 21A342)"
        val versionStr = NSProcessInfo.processInfo.operatingSystemVersionString
        val majorVersion = versionStr.substringAfter("Version ")
            .substringBefore(".").trim().toIntOrNull() ?: 0
        val isIos14OrNewer = majorVersion >= 14
        return suspendCancellableCoroutine { cont ->
            if (isIos14OrNewer) {
                // iOS 14+: use access-level API (non-deprecated)
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
                    val result = when (status) {
                        PHAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
                        PHAuthorizationStatusLimited       -> GrantStatus.PARTIAL_GRANTED
                        PHAuthorizationStatusDenied,
                        PHAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
                        PHAuthorizationStatusNotDetermined -> GrantStatus.DENIED
                        else -> GrantStatus.DENIED
                    }
                    mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
                }
            } else {
                // iOS < 14: legacy API
                @Suppress("DEPRECATION")
                PHPhotoLibrary.requestAuthorization { status ->
                    val result = when (status) {
                        PHAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
                        PHAuthorizationStatusLimited       -> GrantStatus.PARTIAL_GRANTED
                        PHAuthorizationStatusDenied,
                        PHAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
                        else -> GrantStatus.DENIED
                    }
                    mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
                }
            }
        }
    }

    // --- Location Request (delegate-based) ---
    private suspend fun requestLocationAccess(forAlways: Boolean): GrantStatus {
        val requiredKey = if (forAlways) "NSLocationAlwaysAndWhenInUseUsageDescription"
                          else "NSLocationWhenInUseUsageDescription"
        if (!hasInfoPlistKey(requiredKey)) return GrantStatus.DENIED_ALWAYS

        val clStatus: CLAuthorizationStatus = if (forAlways) {
            locationDelegate.requestAlwaysAuthorization()
        } else {
            locationDelegate.requestWhenInUseAuthorization()
        }

        return when (clStatus) {
            kCLAuthorizationStatusAuthorizedAlways    -> GrantStatus.GRANTED
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                if (forAlways) GrantStatus.PARTIAL_GRANTED else GrantStatus.GRANTED
            }
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted -> GrantStatus.DENIED_ALWAYS
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
        if (!hasInfoPlistKey("NSContactsUsageDescription")) return GrantStatus.DENIED_ALWAYS
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

    /**
     * FIX H1: Calendar request on iOS 17+ — `requestAccessToEntityType` callback
     * returns `granted=false` for BOTH "Don't Allow" AND "Add Events Only" (writeOnly).
     *
     * Fix: Ignore the `granted` boolean entirely. After the system dialog completes,
     * re-read the actual authorization status via `checkCalendarStatus()`, which
     * correctly maps raw value 4 (writeOnly) → PARTIAL_GRANTED.
     */
    private suspend fun requestCalendarAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSCalendarsUsageDescription") &&
            !hasInfoPlistKey("NSCalendarsFullAccessUsageDescription")) {
            return GrantStatus.DENIED_ALWAYS
        }
        return suspendCancellableCoroutine { cont ->
            val eventStore = EKEventStore()
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { _, error ->
                if (error != null) {
                    GrantLogger.e(TAG, "Calendar request error: ${error.localizedDescription}")
                }
                // FIX: Re-read actual status instead of trusting `granted` boolean.
                // On iOS 17+, granted=false for writeOnly (Add Events Only) — must check raw value.
                val result = checkCalendarStatus()
                mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
            }
        }
    }

    /**
     * FIX M7: Add `invokeOnCancellation` to release `activityManager` reference
     * if the calling coroutine is cancelled before the query callback fires.
     * This prevents the query from calling into a dangling continuation.
     */
    private suspend fun requestMotionAccess(): GrantStatus {
        if (!hasInfoPlistKey("NSMotionUsageDescription")) return GrantStatus.DENIED_ALWAYS
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(TAG, "Simulator detected — returning GRANTED for Motion request.")
            return GrantStatus.GRANTED
        }
        if (!CMMotionActivityManager.isActivityAvailable()) return GrantStatus.DENIED_ALWAYS

        // Dummy Query pattern — triggers the system permission dialog.
        // Same approach used by MOKO-permissions and Flutter permission_handler.
        return suspendCancellableCoroutine { cont ->
            val activityManager = CMMotionActivityManager()
            val now = NSDate()

            cont.invokeOnCancellation {
                // FIX M7: Stop the manager so the query callback won't fire after cancellation
                activityManager.stopActivityUpdates()
                GrantLogger.i(TAG, "Motion dummy query cancelled — activityManager stopped.")
            }

            activityManager.queryActivityStartingFromDate(
                start = now,
                toDate = now,
                toQueue = NSOperationQueue.mainQueue
            ) { _, _ ->
                val status = CMMotionActivityManager.authorizationStatus()
                val result = when (status) {
                    CMAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
                    CMAuthorizationStatusDenied,
                    CMAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
                    CMAuthorizationStatusNotDetermined -> GrantStatus.DENIED
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
     * FIX L8: Single logging point — callers no longer log their own error messages,
     * keeping `hasInfoPlistKey` as the sole, consistent logger for missing plist keys.
     *
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
