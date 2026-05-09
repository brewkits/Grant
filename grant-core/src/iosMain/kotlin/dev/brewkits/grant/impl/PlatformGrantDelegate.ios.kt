package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.LocationManagerDelegate
import dev.brewkits.grant.handlers.AVPermissionHandler
import dev.brewkits.grant.handlers.BluetoothPermissionHandler
import dev.brewkits.grant.handlers.CalendarPermissionHandler
import dev.brewkits.grant.handlers.ContactsPermissionHandler
import dev.brewkits.grant.handlers.IosPermissionHandler
import dev.brewkits.grant.handlers.LocationPermissionHandler
import dev.brewkits.grant.handlers.MotionPermissionHandler
import dev.brewkits.grant.handlers.NotificationPermissionHandler
import dev.brewkits.grant.handlers.PhotoPermissionHandler
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.runOnMain
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.Foundation.NSURL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.NSBundle
import platform.Foundation.NSClassFromString
import platform.Foundation.valueForKey

import dev.brewkits.grant.utils.ReentrantMutex

private const val TAG = "iOSGrantDelegate"

/**
 * iOS Platform Grant Delegate — Production-ready implementation.
 *
 * Dispatches permission operations to dedicated [IosPermissionHandler] instances,
 * each of which owns exactly the native framework imports it needs. This ensures
 * that unused permission frameworks are **not statically linked** into the app binary,
 * preventing Apple App Store rejections caused by undeclared usage description keys.
 *
 * See [docs/ios/APPLE_FRAMEWORK_LINKING_ISSUE.md] for the full architectural rationale.
 *
 * **Supported permissions (17 total):**
 * - Camera / Microphone — AVFoundation
 * - Gallery / Storage   — Photos
 * - Location (WhenInUse / Always) — CoreLocation
 * - Notification        — UserNotifications
 * - Contacts            — Contacts
 * - Calendar            — EventKit
 * - Motion              — CoreMotion
 * - Bluetooth           — CoreBluetooth (via BluetoothManagerDelegate)
 * - Schedule Exact Alarm — always GRANTED on iOS
 *
 * **Thread Safety:**
 * - [mutexMap] is protected by [mapsMutex]
 * - [statusCacheMap] uses per-entry locking via [mapsMutex]
 * - All synchronous iOS framework calls dispatch to the main thread via [runOnMain]
 * - Notification status is checked via its own async path to avoid nested dispatch
 */
@Suppress("UnusedPrivateProperty")
actual class PlatformGrantDelegate(
    private val store: GrantStore
) {

    // ====================================================================
    // Thread-safe maps
    // ====================================================================

    /** Protects all map structures (mutexMapInternal, statusCacheMap). */
    private val mapsMutex = Mutex()
    private val mutexMapInternal = mutableMapOf<String, ReentrantMutex>()
    private val statusCacheMap = mutableMapOf<String, Pair<GrantStatus, Long>>()

    private companion object {
        /** Status cache TTL (200ms). Prevents redundant OS calls within the same interaction while remaining fresh. */
        const val STATUS_CACHE_TTL_MS = 200L
    }

    private suspend fun getMutexFor(identifier: String): ReentrantMutex =
        mapsMutex.withLock { mutexMapInternal.getOrPut(identifier) { ReentrantMutex() } }

    private fun getMonotonicTimeMillis(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    // ====================================================================
    // Shared delegates (lazy — only initialized if their permission is used)
    // ====================================================================

    private val locationDelegate by lazy { LocationManagerDelegate() }
    private val bluetoothDelegate by lazy { BluetoothManagerDelegate() }

    // ====================================================================
    // Handlers (lazy — framework code loaded only when first accessed)
    // ====================================================================

    private val cameraHandler by lazy { AVPermissionHandler.camera() }
    private val microphoneHandler by lazy { AVPermissionHandler.microphone() }
    private val photoHandler by lazy { PhotoPermissionHandler() }
    private val locationWhenInUseHandler by lazy { LocationPermissionHandler(forAlways = false, delegate = locationDelegate) }
    private val locationAlwaysHandler by lazy { LocationPermissionHandler(forAlways = true, delegate = locationDelegate) }
    private val notificationHandler by lazy { NotificationPermissionHandler() }
    private val contactsHandler by lazy { ContactsPermissionHandler() }
    private val calendarHandler by lazy { CalendarPermissionHandler() }
    private val motionHandler by lazy { MotionPermissionHandler() }
    private val bluetoothHandler by lazy { BluetoothPermissionHandler(delegate = bluetoothDelegate) }

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        return getMutexFor(identifier).withLock {
            mapsMutex.withLock {
                statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
                    if (getMonotonicTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) return@withLock cachedStatus
                }
            }
            
            // Perform actual status check logic inline
            val status = if (grant is RawPermission) {
                val iosKey = grant.iosUsageKey
                if (iosKey != null && !hasInfoPlistKey(iosKey)) GrantStatus.DENIED_ALWAYS
                else if (store.isRawPermissionRequested(grant.identifier)) GrantStatus.DENIED
                else GrantStatus.NOT_DETERMINED
            } else if ((grant as AppGrant) == AppGrant.NOTIFICATION) {
                notificationHandler.checkStatusAsync()
            } else {
                runOnMain { handlerFor(grant).checkStatus() }
            }

            mapsMutex.withLock { statusCacheMap[identifier] = status to getMonotonicTimeMillis() }
            status
        }
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus =
        getMutexFor(grant.identifier).withLock { requestInternal(grant) }

    actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        if (grants.size > 1) {
            GrantLogger.w(TAG, "Requesting multiple grants sequentially. This will trigger multiple system dialogs in a row. Consider requesting them individually for better UX.")
        }
        val results = mutableMapOf<GrantPermission, GrantStatus>()
        grants.forEach { results[it] = request(it) }
        return results
    }

    /**
     * Opens the app's settings page in iOS Settings.
     * Logs a warning if the Settings URL cannot be resolved (e.g., App Extensions, App Clips).
     */
    actual fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        if (url == null) {
            GrantLogger.w(TAG, "UIApplicationOpenSettingsURLString could not be resolved. Settings cannot be opened.")
            return
        }

        // Check for App Extension or other restricted contexts
        val isExtension = NSBundle.mainBundle.bundlePath.endsWith(".appex")
        if (isExtension) {
            GrantLogger.w(TAG, "Cannot open Settings from an App Extension context.")
            return
        }

        dispatch_async(dispatch_get_main_queue()) {
            try {
                // Safely access sharedApplication via KVC to prevent crashes in App Clips or Siri contexts
                // where the static accessor is forbidden and throws uncatchable native exceptions.
                val sharedApp = (NSClassFromString("UIApplication") as? platform.darwin.NSObject)?.valueForKey("sharedApplication") as? UIApplication
                if (sharedApp == null) {
                    GrantLogger.w(TAG, "UIApplication.sharedApplication is not accessible in this context.")
                    return@dispatch_async
                }
                
                sharedApp.openURL(
                    url,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = { success ->
                        if (!success) {
                            GrantLogger.w(TAG, "openURL to Settings returned false — Settings may not be accessible in this context.")
                        }
                    }
                )
            } catch (e: Exception) {
                GrantLogger.e(TAG, "Crash prevented when opening settings", e)
            }
        }
    }

    // ====================================================================
    // REQUEST LOGIC
    // ====================================================================

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }

        // Since getMutexFor() returns a ReentrantMutex, calling the public checkStatus() 
        // here is now safe and will not deadlock.
        val currentStatus = checkStatus(grant)
        if (currentStatus == GrantStatus.GRANTED || currentStatus == GrantStatus.PARTIAL_GRANTED) {
            return currentStatus
        }
        if (currentStatus == GrantStatus.DENIED_ALWAYS) {
            GrantLogger.w(TAG, "Grant '${grant.identifier}' is permanently denied. User must enable in Settings.")
            return currentStatus
        }

        if (grant is RawPermission) {
            val customHandler = IosPermissionHandlerRegistry.get(grant.identifier)
            if (customHandler != null) {
                val result = customHandler.request()
                mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }
                return result
            }
            
            GrantLogger.w(TAG, "RawPermission '${grant.identifier}' on iOS: no generic request API available. " +
                "Implement a custom IosPermissionHandler for this permission and register it via IosPermissionHandlerRegistry.")
            return GrantStatus.NOT_DETERMINED
        }

        val result = when (grant as AppGrant) {
            AppGrant.NOTIFICATION -> notificationHandler.request()
            else                  -> handlerFor(grant).request()
        }

        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }
        return result
    }

    // ====================================================================
    // HANDLER DISPATCH
    // ====================================================================

    /**
     * Returns the appropriate [IosPermissionHandler] for the given [AppGrant].
     *
     * Note: [AppGrant.NOTIFICATION] is handled separately in [checkStatusInternal]
     * and [requestInternal] due to its async-only check path.
     */
    private fun handlerFor(grant: AppGrant): IosPermissionHandler = when (grant) {
        AppGrant.CAMERA               -> cameraHandler
        AppGrant.MICROPHONE           -> microphoneHandler

        AppGrant.GALLERY,
        AppGrant.STORAGE,
        AppGrant.GALLERY_IMAGES_ONLY,
        AppGrant.GALLERY_VIDEO_ONLY   -> photoHandler

        AppGrant.LOCATION             -> locationWhenInUseHandler
        AppGrant.LOCATION_ALWAYS      -> locationAlwaysHandler

        AppGrant.NOTIFICATION         -> notificationHandler   // unreachable here; handled above

        AppGrant.CONTACTS,
        AppGrant.READ_CONTACTS        -> contactsHandler

        AppGrant.CALENDAR,
        AppGrant.READ_CALENDAR        -> calendarHandler

        AppGrant.MOTION               -> motionHandler

        AppGrant.BLUETOOTH,
        AppGrant.BLUETOOTH_ADVERTISE  -> bluetoothHandler

        AppGrant.SCHEDULE_EXACT_ALARM,
        AppGrant.NEARBY_WIFI_DEVICES  -> AlwaysGrantedHandler
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    /**
     * Validates that a required Info.plist key is present.
     * Delegates to the shared [dev.brewkits.grant.utils.hasInfoPlistKey] utility.
     */
    private fun hasInfoPlistKey(key: String): Boolean = hasInfoPlistKey(TAG, key)
}

// ────────────────────────────────────────────────────────────────────────────
// Singleton handler for permissions that are always granted on iOS
// ────────────────────────────────────────────────────────────────────────────

/**
 * A no-op handler for permissions that iOS always grants without a dialog
 * (e.g., [AppGrant.SCHEDULE_EXACT_ALARM]).
 */
private object AlwaysGrantedHandler : IosPermissionHandler {
    override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
    override suspend fun request(): GrantStatus = GrantStatus.GRANTED
}
