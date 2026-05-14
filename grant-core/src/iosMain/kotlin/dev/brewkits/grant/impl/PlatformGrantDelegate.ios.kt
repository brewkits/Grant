package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantBuilder
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.handlers.IosPermissionHandler
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.NotificationPermissionHandler
import dev.brewkits.grant.registerAll
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
 * iOS Platform Grant Delegate — registry-driven dispatch.
 *
 * Handlers are supplied as a map of [AppGrant] → factory function. The map is
 * populated by [GrantBuilder] (called from `GrantFactory.create { … }`) and
 * each per-permission `actual fun` references only the handler classes for that
 * permission family. Permissions the consumer did not register are not in the
 * map, and the K/N linker DCEs their handler classes — along with the
 * `import platform.X.*` declarations inside those handlers — out of the
 * release binary. This prevents Apple's static analyzer from demanding
 * `NSUsageDescription` keys for frameworks the app never actually uses.
 *
 * See [issue #38](https://github.com/brewkits/Grant/issues/38) and
 * `docs/ios/APPLE_FRAMEWORK_LINKING_ISSUE.md` for the architectural rationale.
 *
 * **Thread Safety:**
 * - [mutexMapInternal] / [statusCacheMap] guarded by [mapsMutex]
 * - All synchronous iOS framework calls dispatch to the main thread via [runOnMain]
 * - Notification status uses its own async path to avoid nested dispatch
 */
@Suppress("UnusedPrivateProperty")
actual class PlatformGrantDelegate internal constructor(
    private val store: GrantStore,
    private val handlerFactories: Map<AppGrant, () -> IosPermissionHandler>
) {
    /**
     * Legacy constructor — registers every built-in handler. Preserves
     * pre-v1.5.0 linking behavior for callers that still use
     * `GrantFactory.create()` (no-arg) or construct the delegate directly.
     *
     * The class-reachability through [registerAll] forces the K/N linker to
     * keep every handler; this is the correct trade-off for the legacy path
     * so existing apps don't break. Apps that need DCE savings must switch to
     * the new `GrantFactory.create { … }` form.
     */
    constructor(store: GrantStore) : this(store, buildLegacyRegistry())

    // ====================================================================
    // Thread-safe maps
    // ====================================================================

    /** Protects all map structures (mutexMapInternal, statusCacheMap, handlerCache). */
    private val mapsMutex = Mutex()
    private val mutexMapInternal = mutableMapOf<String, ReentrantMutex>()
    private val statusCacheMap = mutableMapOf<String, Pair<GrantStatus, Long>>()

    /** Lazy cache of instantiated handlers — each factory runs at most once. */
    private val handlerCache = mutableMapOf<AppGrant, IosPermissionHandler>()

    private companion object {
        /** Status cache TTL (1000ms). */
        const val STATUS_CACHE_TTL_MS = 1000L
        /** Delay between sequential requests on iOS to avoid system throttling. */
        const val SEQUENTIAL_REQUEST_DELAY_MS = 600L

        /**
         * Builds the legacy "register everything" map used by the no-arg
         * constructor. Importantly this calls [registerAll], which references
         * every per-permission extension — preserving v1.4.x linking behavior
         * so existing apps stay functional after upgrading.
         */
        private fun buildLegacyRegistry(): Map<AppGrant, () -> IosPermissionHandler> {
            val builder = GrantBuilder().apply { registerAll() }
            return builder.registrations.mapValues { (_, factory) ->
                { factory() as IosPermissionHandler }
            }
        }
    }

    private suspend fun getMutexFor(identifier: String): ReentrantMutex =
        mapsMutex.withLock { mutexMapInternal.getOrPut(identifier) { ReentrantMutex() } }

    private fun getMonotonicTimeMillis(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    /**
     * Returns the handler for [grant], instantiating it lazily via its
     * registered factory. Throws an informative error if the consumer did not
     * register the permission.
     *
     * NOTIFICATION has a separate async path (see [requestInternal]) but its
     * factory still lives in the map for status calls via [notificationHandler].
     */
    private suspend fun handlerFor(grant: AppGrant): IosPermissionHandler =
        mapsMutex.withLock {
            handlerCache.getOrPut(grant) {
                val factory = handlerFactories[grant]
                    ?: error(
                        "Permission ${grant.name} not registered. " +
                            "Add ${suggestedExtension(grant)}() to your GrantFactory.create { } block, " +
                            "or call registerAll() for the legacy behavior."
                    )
                factory()
            }
        }

    /**
     * Maps an [AppGrant] back to the [GrantBuilder] extension that registers it.
     * Used purely for the not-registered error message.
     */
    private fun suggestedExtension(grant: AppGrant): String = when (grant) {
        AppGrant.CAMERA -> "camera"
        AppGrant.MICROPHONE -> "microphone"
        AppGrant.GALLERY,
        AppGrant.GALLERY_IMAGES_ONLY,
        AppGrant.GALLERY_VIDEO_ONLY -> "gallery"
        AppGrant.STORAGE -> "storage"
        AppGrant.LOCATION -> "location"
        AppGrant.LOCATION_ALWAYS -> "locationAlways"
        AppGrant.NOTIFICATION -> "notification"
        AppGrant.SCHEDULE_EXACT_ALARM -> "scheduleExactAlarm"
        AppGrant.BLUETOOTH,
        AppGrant.BLUETOOTH_ADVERTISE -> "bluetooth"
        AppGrant.CONTACTS,
        AppGrant.READ_CONTACTS -> "contacts"
        AppGrant.CALENDAR,
        AppGrant.READ_CALENDAR -> "calendar"
        AppGrant.MOTION -> "motion"
        AppGrant.NEARBY_WIFI_DEVICES -> "nearbyWifiDevices"
    }

    /**
     * Returns the notification handler (used by the async-only check path).
     * Throws if NOTIFICATION was not registered.
     */
    private suspend fun notificationHandler(): NotificationPermissionHandler {
        val handler = handlerFor(AppGrant.NOTIFICATION)
        check(handler is NotificationPermissionHandler) {
            "Registered NOTIFICATION handler is not a NotificationPermissionHandler"
        }
        return handler
    }

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
                notificationHandler().checkStatusAsync()
            } else {
                val handler = handlerFor(grant)
                runOnMain { handler.checkStatus() }
            }

            mapsMutex.withLock { statusCacheMap[identifier] = status to getMonotonicTimeMillis() }
            status
        }
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus =
        getMutexFor(grant.identifier).withLock { requestInternal(grant) }

    actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        if (grants.isEmpty()) return emptyMap()
        if (grants.size == 1) return mapOf(grants.first() to request(grants.first()))

        GrantLogger.d(TAG, "Requesting ${grants.size} grants sequentially on iOS.")

        val sortedGrants = grants.distinctBy { it.identifier }.sortedBy { it.identifier }
        return lockAllIterative(sortedGrants) {
            val results = mutableMapOf<GrantPermission, GrantStatus>()
            grants.forEachIndexed { index, grant ->
                results[grant] = requestInternal(grant)

                // UX Improvement: Add a small delay between sequential dialogs on iOS.
                if (index < grants.size - 1) {
                    kotlinx.coroutines.delay(SEQUENTIAL_REQUEST_DELAY_MS)
                }
            }
            results
        }
    }

    /**
     * Iterative locking to prevent O(n) stack growth and avoid deadlocks.
     */
    private suspend fun <T> lockAllIterative(grants: List<GrantPermission>, block: suspend () -> T): T {
        val chain: suspend () -> T = grants.foldRight(block) { grant, inner ->
            suspend { getMutexFor(grant.identifier).withLock { inner() } }
        }
        return chain()
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
            AppGrant.NOTIFICATION -> notificationHandler().request()
            else                  -> handlerFor(grant).request()
        }

        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }
        return result
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    /**
     * Validates that a required Info.plist key is present.
     * Delegates to the shared [dev.brewkits.grant.utils.hasInfoPlistKey] utility.
     */
    private fun hasInfoPlistKey(key: String): Boolean = hasInfoPlistKey(TAG, key)

    actual fun setLauncher(launcher: GrantLauncher) {}
}
