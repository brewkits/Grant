package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.handlers.PermissionHandler
import dev.brewkits.grant.handlers.NotificationPermissionHandler
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
 * Dispatches permission operations to [PermissionHandler] instances. Every handler that needs a
 * privacy-sensitive framework lives in an opt-in module and reaches this class only through
 * [IosPermissionHandlerRegistry], so this file must never reference such a handler directly.
 * Any direct reference, even behind `by lazy`, makes every consuming app link that framework
 * (Issue #91). `scripts/check-ios-linkage.sh` checks this in CI.
 *
 * See [docs/ios/APPLE_FRAMEWORK_LINKING_ISSUE.md] for the full architectural rationale.
 *
 * **Where each permission is handled:**
 * - Notification — here (UserNotifications; no usage-description key involved)
 * - Camera / Microphone — `grant-camera` (AVFoundation)
 * - Gallery / Storage — `grant-gallery` (Photos)
 * - Location (when in use) — `grant-location`; always — `grant-location-always` (CoreLocation)
 * - Contacts / Calendar / Motion / Bluetooth / App Tracking — their own modules
 * - Schedule Exact Alarm — GRANTED unless the app declares NSAlarmKitUsageDescription;
 *   see ExactAlarmHandler for why AlarmKit (iOS 26+) cannot be answered from here
 *
 * **Thread Safety:**
 * - [mutexMap] is protected by [mapsMutex]
 * - [statusCacheMap] uses per-entry locking via [mapsMutex]
 * - All synchronous iOS framework calls dispatch to the main thread via [runOnMain]
 * - Notification status is checked via its own async path to avoid nested dispatch
 */
@Suppress("UnusedPrivateProperty")
public actual class PlatformGrantDelegate(
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
        /** Status cache TTL (1000ms). */
        const val STATUS_CACHE_TTL_MS = 1000L
        /** Delay between sequential requests on iOS to avoid system throttling. */
        const val SEQUENTIAL_REQUEST_DELAY_MS = 600L
    }

    private suspend fun getMutexFor(identifier: String): ReentrantMutex =
        mapsMutex.withLock { mutexMapInternal.getOrPut(identifier) { ReentrantMutex() } }

    private fun getMonotonicTimeMillis(): Long =
        (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

    // ====================================================================
    // Handlers
    // ====================================================================

    // The only framework-backed handler left in grant-core. UserNotifications needs no
    // usage-description key, so linking it costs a consuming app nothing at review time.
    private val notificationHandler by lazy { NotificationPermissionHandler() }

    // ====================================================================
    // PUBLIC API
    // ====================================================================

    /**
     * Reads the current status. **Does not take the per-permission mutex.**
     *
     * It used to share that mutex with [request], which a pending system dialog holds until the user
     * answers, so any status poll for the same permission (a UI refresh, another screen) hung behind
     * the dialog. Confirmed on a physical iPhone: with the dialog open, a polling `checkStatus` printed
     * nothing at all. A read has nothing to protect — each handler's read runs on the main thread,
     * and the shared cache is guarded by [mapsMutex] — and the OS state while a dialog is open is
     * simply "not yet answered", which is what an honest read should report.
     */
    public actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        mapsMutex.withLock {
            statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
                if (getMonotonicTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) return cachedStatus
            }
        }

        val status = if (grant is RawPermission) {
            // Registered handlers take precedence — they own both checkStatus and request.
            val registeredHandler = IosPermissionHandlerRegistry.get(grant.identifier)
            if (registeredHandler != null) {
                runOnMain { registeredHandler.checkStatus() }
            } else {
                val iosKey = grant.iosUsageKey
                if (iosKey != null && !hasInfoPlistKey(iosKey)) GrantStatus.DENIED_ALWAYS
                else if (store.isRawPermissionRequested(grant.identifier)) GrantStatus.DENIED
                else GrantStatus.NOT_DETERMINED
            }
        } else if ((grant as AppGrant) == AppGrant.NOTIFICATION) {
            notificationHandler.checkStatusAsync()
        } else {
            runOnMain { handlerFor(grant).checkStatus() }
        }

        mapsMutex.withLock { statusCacheMap[identifier] = status to getMonotonicTimeMillis() }
        return status
    }

    public actual suspend fun request(grant: GrantPermission): GrantStatus =
        getMutexFor(grant.identifier).withLock { requestInternal(grant) }

    public actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
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
    /** One per-app settings page here; there is nothing grant-specific to open. */
    public actual fun openSettings(grant: GrantPermission) { openSettings() }

    public actual fun openSettings() {
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
            
            // Nothing here can ever show a prompt. NOT_DETERMINED would invite an endless retry and
            // never leave that state (nothing is recorded), so report it as unavailable instead.
            GrantLogger.e(TAG, "RawPermission '${grant.identifier}' on iOS: no generic request API available. " +
                "Implement a custom PermissionHandler for this permission and register it via IosPermissionHandlerRegistry. " +
                "Reporting DENIED_ALWAYS.")
            return GrantStatus.DENIED_ALWAYS
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
     * Returns the appropriate [PermissionHandler] for the given [AppGrant].
     *
     * Note: [AppGrant.NOTIFICATION] is handled separately in [checkStatusInternal]
     * and [requestInternal] due to its async-only check path.
     */
    private fun handlerFor(grant: AppGrant): PermissionHandler = when (grant) {
        // Moved out of grant-core in 3.0.0 (Issue #91). Unlike the modules below, these were
        // built in through 2.x, so an app upgrading without adding the module gets a debug-build
        // crash rather than only a log line; see NotRegisteredHandler.
        AppGrant.CAMERA,
        AppGrant.MICROPHONE           -> getOptionalHandler(grant, "dev.brewkits:grant-camera", "GrantCamera.initialize()", failFastInDebug = true)

        AppGrant.GALLERY,
        AppGrant.STORAGE,
        AppGrant.GALLERY_IMAGES_ONLY,
        AppGrant.GALLERY_VIDEO_ONLY,
        AppGrant.GALLERY_ADD_ONLY     -> getOptionalHandler(grant, "dev.brewkits:grant-gallery", "GrantGallery.initialize()", failFastInDebug = true)

        AppGrant.LOCATION             -> getOptionalHandler(grant, "dev.brewkits:grant-location", "GrantLocation.initialize()", failFastInDebug = true)
        AppGrant.LOCATION_ALWAYS      -> getOptionalHandler(grant, "dev.brewkits:grant-location-always", "GrantLocationAlways.initialize()")

        AppGrant.NOTIFICATION         -> notificationHandler   // unreachable here; handled above

        AppGrant.CONTACTS,
        AppGrant.READ_CONTACTS        -> getOptionalHandler(grant, "dev.brewkits:grant-contacts", "GrantContacts.initialize()")

        AppGrant.CALENDAR,
        AppGrant.READ_CALENDAR        -> getOptionalHandler(grant, "dev.brewkits:grant-calendar", "GrantCalendar.initialize()")

        AppGrant.MOTION               -> getOptionalHandler(grant, "dev.brewkits:grant-motion", "GrantMotion.initialize()")

        // AppTrackingTransparency is isolated for the same reason Contacts/EventKit/CoreMotion
        // are: linking it makes Apple require NSUserTrackingUsageDescription in every consuming
        // app, including ones that never track (Issues #38, #45).
        AppGrant.APP_TRACKING         -> getOptionalHandler(grant, "dev.brewkits:grant-tracking", "GrantTracking.initialize()")

        // iOS has ONE Bluetooth authorization covering scan, connect and advertise alike, so
        // all four values share a handler. The scan/connect split is an Android-only
        // distinction (API 31 separated them); mirroring it here would invent a difference
        // the platform does not have.
        AppGrant.BLUETOOTH,
        AppGrant.BLUETOOTH_SCAN,
        AppGrant.BLUETOOTH_CONNECT,
        AppGrant.BLUETOOTH_ADVERTISE  -> getOptionalHandler(grant, "dev.brewkits:grant-bluetooth", "GrantBluetooth.initialize()")

        // Registry first, so an app that bridges AlarmKit itself wins over the fallback below.
        AppGrant.SCHEDULE_EXACT_ALARM -> IosPermissionHandlerRegistry.get(grant.identifier) ?: ExactAlarmHandler

        // iOS has a real Local Network permission the user can deny, but no API to query or
        // request it — the OS prompts on the first LAN access when NSLocalNetworkUsageDescription
        // is present. Grant therefore cannot know the answer; see LocalNetworkHandler.
        AppGrant.LOCAL_NETWORK        -> LocalNetworkHandler

        // Android-only concept (no iOS counterpart to query).
        AppGrant.NEARBY_WIFI_DEVICES,
        // Android-only concept (full-screen intent from a notification, gated on API 34+).
        // iOS has no separate authorization for it beyond the standard NOTIFICATION one.
        AppGrant.USE_FULL_SCREEN_INTENT -> AlwaysGrantedHandler
    }

    private fun getOptionalHandler(
        grant: AppGrant,
        moduleName: String,
        initCode: String,
        failFastInDebug: Boolean = false,
    ): PermissionHandler {
        return IosPermissionHandlerRegistry.get(grant.identifier)
            ?: NotRegisteredHandler(grant, moduleName, initCode, failFastInDebug)
    }

    // ====================================================================
    // UTILITIES
    // ====================================================================

    /**
     * Validates that a required Info.plist key is present.
     * Delegates to the shared [dev.brewkits.grant.utils.hasInfoPlistKey] utility.
     */
    private fun hasInfoPlistKey(key: String): Boolean = hasInfoPlistKey(TAG, key)

    public actual fun setLauncher(launcher: GrantLauncher) {}
}

// ────────────────────────────────────────────────────────────────────────────
// Singleton handler for permissions that are always granted on iOS
// ────────────────────────────────────────────────────────────────────────────

/**
 * A no-op handler for permissions that iOS always grants without a dialog
 * (e.g., [AppGrant.NEARBY_WIFI_DEVICES]).
 */
private object AlwaysGrantedHandler : PermissionHandler {
    override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
    override suspend fun request(): GrantStatus = GrantStatus.GRANTED
}

/**
 * [AppGrant.LOCAL_NETWORK] on iOS. The user can deny it, but iOS exposes no API to read or
 * request that state, so the only honest answer is "unknown" — never GRANTED, which would be
 * fabricated (the same rule the web and desktop delegates follow).
 *
 * NOT_DETERMINED rather than DENIED_ALWAYS: access may well be allowed, and the OS prompts on
 * the first local-network use. A caller that must react to a denial has to observe the failure
 * of its own network call.
 */
private object LocalNetworkHandler : PermissionHandler {
    private fun unknown(): GrantStatus {
        GrantLogger.w(
            TAG,
            "LOCAL_NETWORK: iOS provides no API to read or request local-network authorization, " +
                "so its state is unknown. Reporting NOT_DETERMINED instead of a fabricated GRANTED; " +
                "the system prompts on first LAN access when NSLocalNetworkUsageDescription is set.",
        )
        return GrantStatus.NOT_DETERMINED
    }

    override fun checkStatus(): GrantStatus = unknown()
    override suspend fun request(): GrantStatus = unknown()
}

/** `Info.plist` key an app declares when it uses AlarmKit (iOS 26+). */
private const val ALARMKIT_USAGE_KEY = "NSAlarmKitUsageDescription"

/**
 * Handles [AppGrant.SCHEDULE_EXACT_ALARM] on iOS, where the honest answer depends on whether
 * the app uses AlarmKit.
 *
 * **Until iOS 25** nothing on iOS gated scheduling: a local notification via
 * `UNUserNotificationCenter` needs only the notification permission Grant already models, so
 * reporting GRANTED here was correct and remains correct for those apps.
 *
 * **iOS 26 introduced AlarmKit**, which *is* consent-gated — the SDK exposes
 * `requestAuthorization`, `authorizationState` and `AlarmAuthorizationState`, and the runtime
 * defines [ALARMKIT_USAGE_KEY]. For an app using it, an unconditional GRANTED is a fabricated
 * status: the user may never have authorized alarms. That is the failure mode this library
 * exists to prevent, so it is not shipped here either.
 *
 * **Grant cannot query AlarmKit itself.** Verified against the iOS 26.5 SDK: AlarmKit's
 * `Headers/AlarmKit.h` is an umbrella header carrying only version symbols — the whole API
 * lives in `.swiftinterface`, so there is no Objective-C surface for Kotlin/Native cinterop to
 * bind. Claiming support would be presence without function.
 *
 * So the plist key is used as the signal for "this app uses AlarmKit":
 * - key absent (the overwhelming majority) → GRANTED, unchanged behaviour
 * - key present → DENIED_ALWAYS with a log pointing at the escape hatch, matching the
 *   convention the web and JVM delegates already follow for permissions the platform cannot
 *   back. An app that needs a real answer registers its own Swift-bridged handler through
 *   [IosPermissionHandlerRegistry], which takes precedence over this one.
 */
private object ExactAlarmHandler : PermissionHandler {

    private fun resolve(): GrantStatus {
        // Read the bundle directly rather than through hasInfoPlistKey(): that helper logs an
        // ERROR when a key is absent, which is right where a key is required, but here absence
        // is the normal, correct case for almost every app.
        val usesAlarmKit = NSBundle.mainBundle.objectForInfoDictionaryKey(ALARMKIT_USAGE_KEY) != null
        if (!usesAlarmKit) return GrantStatus.GRANTED

        GrantLogger.w(
            TAG,
            "$ALARMKIT_USAGE_KEY is declared, so this app uses AlarmKit — which iOS 26 gates " +
                "behind user authorization. Grant cannot read that state: AlarmKit is a " +
                "Swift-only framework with no Objective-C surface for Kotlin/Native. Reporting " +
                "DENIED_ALWAYS rather than a GRANTED this platform cannot back. To get a real " +
                "answer, implement PermissionHandler over AlarmKit in your iOS code and " +
                "register it via IosPermissionHandlerRegistry.register(" +
                "AppGrant.SCHEDULE_EXACT_ALARM.identifier, yourHandler).",
        )
        return GrantStatus.DENIED_ALWAYS
    }

    override fun checkStatus(): GrantStatus = resolve()
    override suspend fun request(): GrantStatus = resolve()
}

/**
 * A fallback handler for modular permissions that haven't been registered.
 *
 * DENIED_ALWAYS, not NOT_DETERMINED: no prompt can ever appear until the module is added and
 * initialised, so "ask me later" is a status this platform cannot back. Logged at ERROR so a
 * missing initialize() call surfaces in crash reporting instead of looking like a quiet no-op.
 *
 * [failFastInDebug] is set only for CAMERA/MICROPHONE, GALLERY* and LOCATION. Those were built
 * into grant-core until 3.0.0, so an upgrading app that forgets the new module silently starts
 * reporting DENIED_ALWAYS for a permission it used to get. In a debug binary that throws an
 * [AssertionError] instead: an `Error`, not an `Exception`, so the `catch (e: Exception)` blocks
 * in GrantHandler cannot swallow it and the mistake shows up on the developer's first run, not
 * in production. Release builds keep the log-and-deny behaviour. The older opt-in modules keep
 * their existing behaviour; changing theirs would be a separate decision.
 */
private class NotRegisteredHandler(
    private val grant: AppGrant,
    private val moduleName: String,
    private val initCode: String,
    private val failFastInDebug: Boolean,
) : PermissionHandler {

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private fun deny(): GrantStatus {
        val message = "AppGrant.${grant.name} needs module $moduleName on iOS. Add the dependency and call $initCode."
        if (failFastInDebug && Platform.isDebugBinary) {
            throw AssertionError("$message (Thrown in debug builds only; release builds report DENIED_ALWAYS.)")
        }
        GrantLogger.e(TAG, "$message Reporting DENIED_ALWAYS.")
        return GrantStatus.DENIED_ALWAYS
    }

    override fun checkStatus(): GrantStatus = deny()

    override suspend fun request(): GrantStatus = deny()
}
