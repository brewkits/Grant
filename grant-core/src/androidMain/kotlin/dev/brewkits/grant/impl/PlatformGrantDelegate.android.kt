package dev.brewkits.grant.impl

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.PlatformConfig
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

import dev.brewkits.grant.utils.ReentrantMutex

import dev.brewkits.grant.GrantLauncher

public actual class PlatformGrantDelegate(
    private val context: Context,
    private val store: GrantStore
) {
    init {
        trackForegroundActivity(context)
    }

    @Volatile
    private var launcher: GrantLauncher? = null
    public actual fun setLauncher(launcher: GrantLauncher) { this.launcher = launcher }

    /**
     * One mutex per permission identifier, so requests for different permissions never
     * serialise against each other.
     *
     * `computeIfAbsent` rather than a surrounding `Mutex`: the invariant that matters is
     * "exactly one [ReentrantMutex] per identifier", and that is precisely what
     * [ConcurrentHashMap.computeIfAbsent] guarantees atomically. The previous code wrapped
     * this — and every [statusCacheMap] read and write — in a single global `mapsMutex`,
     * which serialised *all* permission checks against one another on the hot path while
     * adding nothing the concurrent map did not already provide.
     */
    private val mutexMapInternal = ConcurrentHashMap<String, ReentrantMutex>()

    private fun getMutexFor(identifier: String): ReentrantMutex =
        mutexMapInternal.computeIfAbsent(identifier) { ReentrantMutex() }

    // Notification status cache with timestamp (Android 12 and below)
    @Volatile
    private var notificationStatusCache: Pair<GrantStatus, Long>? = null

    /**
     * Short-lived status cache, bounded by [MAX_TRACKED_IDENTIFIERS].
     *
     * The bound exists because [RawPermission.identifier] is an arbitrary app-supplied string:
     * an app that mints identifiers dynamically would otherwise grow this map — and
     * [mutexMapInternal] with it — without limit for the lifetime of the process. Access-order
     * `LinkedHashMap` + `removeEldestEntry` evicts the least recently used identifier instead.
     * Eviction is safe here because an entry is only a cached *value*; losing it costs one
     * extra OS call, never correctness. [mutexMapInternal] is deliberately NOT evicted the same
     * way — dropping a mutex another coroutine currently holds would break mutual exclusion —
     * see [pruneMutexesIfNeeded].
     */
    private val statusCacheMap: MutableMap<String, Pair<GrantStatus, Long>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Pair<GrantStatus, Long>>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Pair<GrantStatus, Long>>,
                ): Boolean = size > MAX_TRACKED_IDENTIFIERS
            }
        )

    /**
     * Drops mutexes for identifiers no longer being tracked, but only ones that are currently
     * unlocked — a held mutex must never be replaced, or two coroutines would each get their
     * own instance and mutual exclusion would silently disappear.
     *
     * Called opportunistically after a status write rather than on a timer: the map only grows
     * when new identifiers appear, so that is the only moment pruning can be needed.
     */
    private fun pruneMutexesIfNeeded() {
        if (mutexMapInternal.size <= MAX_TRACKED_IDENTIFIERS) return
        val live = statusCacheMap.keys.toSet()
        mutexMapInternal.entries.removeIf { (id, mutex) -> id !in live && !mutex.isLocked }
    }

    public companion object {
        private const val TAG = "AndroidGrantDelegate"
        private const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        // Android 17 (API 37) — string literal because compileSdk 36 predates the constant.
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

        // TTL for status cache. Increased for better performance,
        // but invalidated manually on every request.
        private const val STATUS_CACHE_TTL_MS = 1000L

        // Maximum time to wait for the Activity Launch Guard to clear
        private const val GUARD_CLEAR_TIMEOUT_MS = 1000L
        private const val GUARD_RETRY_INTERVAL_MS = 50L

        private const val NOTIFICATION_CACHE_TTL_MS = 200L
        private const val SYSTEM_DIALOG_TIMEOUT_MS = 300_000L // 5 minutes (matches activity cleanup)

        /**
         * Upper bound on tracked permission identifiers. Comfortably above the 20 [AppGrant]
         * values plus any realistic set of app-defined [RawPermission]s, so a normal app never
         * evicts; it exists to cap an app that mints identifiers dynamically.
         */
        private const val MAX_TRACKED_IDENTIFIERS = 64

        // Tracks which Application instances already have the lifecycle callback below
        // registered, so creating multiple PlatformGrantDelegate instances (e.g. in tests,
        // or if an app constructs its own GrantManager more than once) never double-registers.
        private val activityTrackedApps = Collections.newSetFromMap(WeakHashMap<Application, Boolean>())

        /**
         * Keeps [PlatformConfig.activity] pointed at the current foreground Activity without
         * requiring any wiring from the consuming app. Called both eagerly from [GrantInitializer]
         * (a ContentProvider that runs before any Activity is created — so it never misses the
         * first `onResume()`) and lazily from [PlatformGrantDelegate]'s constructor as a fallback;
         * the [activityTrackedApps] guard makes the second call a no-op once the first succeeds.
         *
         * Without this, apps that follow the documented pattern of providing an *Application*
         * [Context] (e.g. via Koin's `androidContext(this)`) would never have a live Activity
         * reference, so `shouldShowRequestPermissionRationale()` could never be consulted and
         * DENIED vs DENIED_ALWAYS could never be told apart — including the restart scenario
         * fixed in Issue #55, where that check is the only signal that survives process death.
         */
        @Synchronized
        internal fun trackForegroundActivity(context: Context) {
            val application = context.applicationContext as? Application ?: return
            if (!activityTrackedApps.add(application)) return

            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    PlatformConfig.activity = activity
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
    }

    /**
     * The app's compiled `targetSdkVersion`, used to mirror platform behaviour changes that the
     * OS gates on the target rather than on the device's API level.
     */
    private val targetSdkVersion: Int
        get() = context.applicationInfo.targetSdkVersion

    private fun isPartialGalleryAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    }

    /** "Approximate" location granted: COARSE held while the full [AppGrant.LOCATION] set is not. */
    private fun isApproximateLocationGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    public actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier

        return getMutexFor(identifier).withLock {
            val cached = statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
                if (SystemClock.elapsedRealtime() - timestamp < STATUS_CACHE_TTL_MS) cachedStatus else null
            }
            if (cached != null) return@withLock cached

            val status = when {
                grant is RawPermission -> resolveRawPermission(grant)
                grant is AppGrant -> resolveAppGrant(grant)
                // GrantPermission is sealed, so this is unreachable; reporting NOT_DETERMINED
                // rather than throwing keeps an unknown future subtype from crashing a caller.
                else -> GrantStatus.NOT_DETERMINED
            }

            statusCacheMap[identifier] = status to SystemClock.elapsedRealtime()
            pruneMutexesIfNeeded()
            status
        }
    }

    /**
     * The Activity to consult for `shouldShowRequestPermissionRationale()`, or `null` when the
     * app has none in the foreground.
     *
     * [PlatformConfig.activity] is kept current by the lifecycle callback registered in
     * [trackForegroundActivity]; the cast is the fallback for an app that passed an Activity
     * as the delegate's own context.
     */
    private fun activeActivity(): Activity? =
        PlatformConfig.activity ?: (context as? Activity)

    /**
     * Classifies a permission that is not currently granted into DENIED / DENIED_ALWAYS /
     * NOT_DETERMINED.
     *
     * This ordering is the heart of Issue #55 and its follow-up, and was duplicated three
     * times before being extracted here — once per permission shape, with the copies drifting
     * apart in exactly the way that made the original bug hard to see.
     *
     * `shouldShowRequestPermissionRationale()` is consulted **first** because it is the OS's
     * own live signal and survives process death, unlike the request history in [store]. A
     * second in-session denial therefore reads as permanent immediately, instead of being
     * masked by a stale stored DENIED.
     *
     * @param noActivityFallback what to report when the permission was requested before but no
     *   Activity is available to confirm permanence. Defaults to DENIED, which keeps the
     *   rationale path open rather than sending the user to Settings on a guess.
     */
    private fun classifyDenial(
        permissions: List<String>,
        wasRequestedBefore: Boolean,
        noActivityFallback: () -> GrantStatus = { GrantStatus.DENIED },
    ): GrantStatus {
        val activity = activeActivity()
        val canShowRationale = activity != null &&
            permissions.any { activity.shouldShowRequestPermissionRationale(it) }
        return when {
            canShowRationale -> GrantStatus.DENIED
            wasRequestedBefore -> if (activity == null) noActivityFallback() else GrantStatus.DENIED_ALWAYS
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    private fun resolveRawPermission(grant: RawPermission): GrantStatus {
        val permissions = grant.androidPermissions
        if (permissions.all { isGranted(it) }) return GrantStatus.GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isGranted(READ_MEDIA_VISUAL_USER_SELECTED)
        ) {
            return GrantStatus.PARTIAL_GRANTED
        }

        return classifyDenial(permissions, store.isRawPermissionRequested(grant.identifier))
    }

    private fun resolveAppGrant(appGrant: AppGrant): GrantStatus {
        getGrantStatusOverride(appGrant)?.let { return it }

        if (appGrant == AppGrant.LOCATION_ALWAYS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return resolveLocationAlways(appGrant)
        }

        val androidGrants = appGrant.toAndroidGrants()
        // No mapped permission on this API level means nothing to ask for — e.g. GALLERY_ADD_ONLY
        // needs no permission at all under scoped storage on API 29+.
        if (androidGrants.isEmpty()) return GrantStatus.GRANTED

        // Full access is judged on the REQUIRED permissions only — see toRequiredAndroidGrants().
        // Counting READ_MEDIA_VISUAL_USER_SELECTED here misclassified a fully-granted gallery
        // (IMAGES + VIDEO granted, USER_SELECTED not) as denied -> DENIED_ALWAYS
        // (Issue: Lam gallery P0, 2026-07-09).
        val requiredGrants = appGrant.toRequiredAndroidGrants()
        if (requiredGrants.isNotEmpty() && requiredGrants.all { isGranted(it) }) return GrantStatus.GRANTED

        if (appGrant.isGalleryRead() && isPartialGalleryAccessGranted()) return GrantStatus.PARTIAL_GRANTED

        // 2.3.0: the user picked "Approximate" in the OS dialog — COARSE is granted, FINE is not.
        // The app HAS usable (coarse) location, but the all-granted check above fails and the
        // request-history fallback used to escalate this to DENIED_ALWAYS. Same defect class as
        // the gallery USER_SELECTED misclassification. Android 17's dialog makes the
        // Precise/Approximate choice more prominent, so this state is common.
        if (appGrant == AppGrant.LOCATION && isApproximateLocationGranted()) return GrantStatus.PARTIAL_GRANTED

        return classifyDenial(
            permissions = androidGrants,
            wasRequestedBefore = store.isRequestedBefore(appGrant),
            // Requested before and no rationale -> permanently denied. With no Activity to
            // confirm, fall back to the last status actually observed. (Never requested implies
            // getStatus() is null, since setStatus only ever follows setRequested.)
            noActivityFallback = { store.getStatus(appGrant) ?: GrantStatus.DENIED },
        )
    }

    private fun resolveLocationAlways(appGrant: AppGrant): GrantStatus {
        val hasForeground = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).all { isGranted(it) }
        val hasBackground = isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        return when {
            hasBackground -> GrantStatus.GRANTED
            hasForeground -> GrantStatus.PARTIAL_GRANTED
            else -> classifyDenial(
                permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                wasRequestedBefore = store.isRequestedBefore(appGrant),
            )
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun AppGrant.isGalleryRead(): Boolean =
        this == AppGrant.GALLERY || this == AppGrant.GALLERY_IMAGES_ONLY || this == AppGrant.GALLERY_VIDEO_ONLY

    public actual suspend fun request(grant: GrantPermission): GrantStatus {
        return getMutexFor(grant.identifier).withLock {
            requestInternal(grant)
        }
    }

    public actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        if (grants.isEmpty()) return emptyMap()
        if (grants.size == 1) return mapOf(grants.first() to request(grants.first()))

        val sortedGrants = grants.distinctBy { it.identifier }.sortedBy { it.identifier }
        return lockAllIterative(sortedGrants) {
            requestMultipleInternal(grants)
        }
    }

    private suspend fun <T> lockAllIterative(grants: List<GrantPermission>, block: suspend () -> T): T {
        val chain: suspend () -> T = grants.foldRight(block) { grant, inner ->
            suspend { getMutexFor(grant.identifier).withLock { inner() } }
        }
        return chain()
    }

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        statusCacheMap.remove(grant.identifier)

        var currentStatus = checkStatus(grant)
        if (currentStatus == GrantStatus.GRANTED) return currentStatus

        // SCHEDULE_EXACT_ALARM is NOT a runtime permission — its protectionLevel is
        // `signature|privileged|appop`, so requestPermissions() can never grant it and shows no
        // dialog at all. Verified on a real Android 17 device:
        //   pm grant <pkg> android.permission.SCHEDULE_EXACT_ALARM
        //   -> SecurityException: ... is not a changeable permission type
        // (the same command for CAMERA succeeds).
        //
        // Before this branch existed, request() fell through to requestPermissions(), nothing
        // happened, store.setRequested() marked it asked, and the next checkStatus() escalated
        // it to DENIED_ALWAYS -- sending the user to a Settings page that does not contain the
        // toggle. That is the exact "dead click" this library exists to prevent (Issue #55),
        // reproduced inside Grant's own enum.
        //
        // The platform's actual request flow for special app access is the dedicated Settings
        // screen, so that is what "requesting" means here.
        if (grant == AppGrant.SCHEDULE_EXACT_ALARM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return requestExactAlarmAccess(currentStatus)
        }

        val allPossiblePermissions = when (grant) {
            is RawPermission -> grant.androidPermissions
            is AppGrant -> grant.toAndroidGrants()
            else -> emptyList()
        }
        
        val androidPermissions = allPossiblePermissions.filter {
            dev.brewkits.grant.util.ManifestValidator.isPermissionDeclared(context, it)
        }

        if (androidPermissions.isEmpty()) {
            return currentStatus
        }

        if (grant is AppGrant) store.setRequested(grant)
        else if (grant is RawPermission) store.markRawPermissionRequested(grant.identifier)

        val launcher = this.launcher
        if (launcher != null) {
            val deferred = kotlinx.coroutines.CompletableDeferred<Unit>()
            launcher.launch(androidPermissions) { _ -> deferred.complete(Unit) }
            try {
                withTimeout(SYSTEM_DIALOG_TIMEOUT_MS) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                // Before CancellationException: this is our own timeout, an outcome to absorb.
                GrantLogger.e(TAG, "System dialog timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GrantLogger.e(TAG, "System dialog error", e)
            }
        } else {
            // No GrantLauncher registered — fall back to the self-contained transparent
            // GrantRequestActivity so the system dialog still opens without requiring the
            // app to bind a launcher to its Activity/Fragment lifecycle (Issue #53).
            GrantLogger.d(TAG, "No GrantLauncher registered; using GrantRequestActivity fallback.")
            requestViaActivity(androidPermissions)
        }

        // Invalidate cache immediately after system dialog returns
        statusCacheMap.remove(grant.identifier)

        var finalStatus = checkStatus(grant)

        // Handle 2-step flow for LOCATION_ALWAYS (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
            grant == AppGrant.LOCATION_ALWAYS && 
            finalStatus == GrantStatus.PARTIAL_GRANTED && 
            currentStatus != GrantStatus.PARTIAL_GRANTED) {
            
            var waitTime = 0L
            while (GrantRequestActivity.isAnyActivityActive() && waitTime < GUARD_CLEAR_TIMEOUT_MS) {
                kotlinx.coroutines.delay(GUARD_RETRY_INTERVAL_MS)
                waitTime += GUARD_RETRY_INTERVAL_MS
            }

            val backgroundPermissions = (grant as AppGrant).toAndroidGrants().filter {
                dev.brewkits.grant.util.ManifestValidator.isPermissionDeclared(context, it)
            }
            
            if (backgroundPermissions.isNotEmpty()) {
                val bgRequestId = GrantRequestActivity.requestGrants(context, backgroundPermissions)
                val bgDeferred = GrantRequestActivity.getResultDeferred(bgRequestId)
                
                if (bgDeferred != null) {
                    try {
                        withTimeout(SYSTEM_DIALOG_TIMEOUT_MS) { bgDeferred.await() }
                    } catch (e: TimeoutCancellationException) {
                        GrantLogger.w(TAG, "LOCATION_ALWAYS background step timed out: ${e.message}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        GrantLogger.w(TAG, "LOCATION_ALWAYS background step failed: ${e.message}")
                    } finally {
                        GrantRequestActivity.cleanup(bgRequestId)
                    }
                    statusCacheMap.remove(grant.identifier)
                    finalStatus = checkStatus(grant)
                }
            }
        }

        if (grant is AppGrant) {
            if (finalStatus == GrantStatus.GRANTED) store.clear(grant)
            else store.setStatus(grant, finalStatus)
        }

        return finalStatus
    }

    private suspend fun requestMultipleInternal(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        grants.forEach { statusCacheMap.remove(it.identifier) }

        val allAndroidPermissions = mutableSetOf<String>()
        grants.forEach { grant ->
            if (grant is AppGrant) store.setRequested(grant)
            else if (grant is RawPermission) store.markRawPermissionRequested(grant.identifier)

            val permissions = when (grant) {
                is RawPermission -> grant.androidPermissions
                is AppGrant -> grant.toAndroidGrants()
                else -> emptyList()
            }
            permissions.forEach { perm ->
                if (dev.brewkits.grant.util.ManifestValidator.isPermissionDeclared(context, perm)) {
                    allAndroidPermissions.add(perm)
                }
            }
        }

        if (allAndroidPermissions.isEmpty()) return grants.associateWith { checkStatus(it) }

        val launcher = this.launcher
        if (launcher != null) {
            val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
            launcher.launch(allAndroidPermissions.toList()) { _ -> deferred.complete(true) }
            try {
                withTimeout(SYSTEM_DIALOG_TIMEOUT_MS) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                GrantLogger.e(TAG, "Multi-request timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GrantLogger.e(TAG, "Multi-request failed", e)
            }
        } else {
            // No GrantLauncher registered — fall back to the self-contained transparent
            // GrantRequestActivity so the system dialog still opens without lifecycle binding (Issue #53).
            GrantLogger.d(TAG, "No GrantLauncher registered; using GrantRequestActivity fallback for multi-request.")
            requestViaActivity(allAndroidPermissions.toList())
        }

        grants.forEach { statusCacheMap.remove(it.identifier) }

        return kotlinx.coroutines.coroutineScope {
            grants.map { grant ->
                async { 
                    val finalStatus = checkStatus(grant)
                    if (grant is AppGrant) {
                        if (finalStatus == GrantStatus.GRANTED) store.clear(grant)
                        else store.setStatus(grant, finalStatus)
                    }
                    grant to finalStatus 
                }
            }.awaitAll().toMap()
        }
    }

    /**
     * Fallback request path used when no [GrantLauncher] has been registered via [setLauncher].
     *
     * Launches the self-contained transparent [GrantRequestActivity], which owns its own
     * [androidx.activity.result.ActivityResultLauncher], so the system permission dialog opens
     * from any context (ViewModel, Repository, etc.) without the app having to bind a launcher
     * to an Activity/Fragment lifecycle. Suspends until the dialog resolves; the caller re-reads
     * the real status via [checkStatus] afterwards. (Issue #53)
     */
    private suspend fun requestViaActivity(androidPermissions: List<String>) {
        val requestId = GrantRequestActivity.requestGrants(context, androidPermissions)
        val deferred = GrantRequestActivity.getResultDeferred(requestId)
        if (deferred == null) {
            // Should not happen now that requestGrants() always leaves an entry for the
            // caller, including on the yield path. Returning quietly here is what previously
            // turned a lost launch-guard race into a request that silently never prompted,
            // so it is logged rather than swallowed.
            GrantLogger.w(
                TAG,
                "No pending result for request $requestId — the system dialog was never " +
                    "shown. Reporting the current status unchanged.",
            )
            return
        }
        try {
            withTimeout(SYSTEM_DIALOG_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            // Must be caught BEFORE CancellationException — it is a subclass. This one is our
            // own timeout firing (the user never answered the dialog), which is a normal
            // outcome to absorb; a plain CancellationException means the caller's scope died
            // and must propagate.
            GrantLogger.e(TAG, "GrantRequestActivity fallback timed out", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GrantLogger.e(TAG, "GrantRequestActivity fallback error", e)
        } finally {
            GrantRequestActivity.cleanup(requestId)
        }
    }

    /**
     * Sends the user to the "Alarms & reminders" special-access screen, which is the platform's
     * actual request flow for [AppGrant.SCHEDULE_EXACT_ALARM] — see the call site in
     * [requestInternal] for why `requestPermissions()` cannot work for it.
     *
     * Returns [currentStatus] unchanged: the user is now in Settings and nothing has been
     * decided yet. This mirrors [openSettings], and like it the host is expected to re-read the
     * status when the app resumes — `GrantHandler.onReturnFromSettings()` (or `refreshStatus()`)
     * exists for exactly that. Blocking here to await a Settings round trip would mean guessing
     * when the user is finished; the resume signal the host already has is the honest one.
     */
    private fun requestExactAlarmAccess(currentStatus: GrantStatus): GrantStatus {
        store.setRequested(AppGrant.SCHEDULE_EXACT_ALARM)
        statusCacheMap.remove(AppGrant.SCHEDULE_EXACT_ALARM.identifier)

        return try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.fromParts("package", context.packageName, null)
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            GrantLogger.i(
                TAG,
                "Opened the exact-alarm settings screen. Re-read the status when your app " +
                    "resumes (GrantHandler.onReturnFromSettings()); this call cannot observe " +
                    "the user's choice itself.",
            )
            currentStatus
        } catch (e: Exception) {
            // Some builds (and most work profiles) do not expose the screen at all. Reporting
            // the unchanged status and logging beats pretending the request happened.
            GrantLogger.e(TAG, "Could not open the exact-alarm settings screen", e)
            currentStatus
        }
    }

    public actual fun openSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            GrantLogger.e(TAG, "Failed to open settings", e)
        }
    }

    private fun getGrantStatusOverride(grant: AppGrant): GrantStatus? {
        return when (grant) {
            AppGrant.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                    // DENIED, deliberately not DENIED_ALWAYS. Special app access has no
                    // permanent-denial state: the toggle stays available in Settings forever and
                    // Grant can re-open that exact screen on every request (see
                    // requestExactAlarmAccess). DENIED_ALWAYS would instead drive GrantHandler
                    // down the settings-guide path, whose openSettings() lands on the app-details
                    // page -- which does not contain this toggle. DENIED drives the rationale
                    // path, and re-requesting reopens the correct screen.
                    if (alarmManager != null && alarmManager.canScheduleExactAlarms()) GrantStatus.GRANTED
                    else if (store.isRequestedBefore(grant)) GrantStatus.DENIED else GrantStatus.NOT_DETERMINED
                } else GrantStatus.GRANTED
            }
            AppGrant.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    notificationStatusCache?.let { (status, time) -> if (SystemClock.elapsedRealtime() - time < NOTIFICATION_CACHE_TTL_MS) return status }
                    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    val status = if (enabled) GrantStatus.GRANTED else if (store.isRequestedBefore(grant)) GrantStatus.DENIED_ALWAYS else GrantStatus.NOT_DETERMINED
                    notificationStatusCache = status to SystemClock.elapsedRealtime()
                    status
                } else null
            }
            else -> null
        }
    }

    /**
     * The permissions that must be granted for a [GrantStatus.GRANTED] (full-access) verdict.
     *
     * Differs from [toAndroidGrants] only for the gallery grants on API 34+:
     * `READ_MEDIA_VISUAL_USER_SELECTED` belongs in the REQUEST array (it is what makes the
     * Android 14 "Select photos" option appear in the system dialog) but must NOT gate full
     * access. The OS can grant `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` while leaving
     * `USER_SELECTED` denied (ADB `pm grant`, MDM policy, permission auto-reset edge states) —
     * that IS full access. Counting it made [checkStatus] report a fully-granted gallery as
     * denied, and the store-requested fallback escalated that to DENIED_ALWAYS
     * (found via the Lam gallery P0, 2026-07-09). `USER_SELECTED` granted alone is
     * partial access, handled by [isPartialGalleryAccessGranted].
     */
    internal fun AppGrant.toRequiredAndroidGrants(): List<String> =
        toAndroidGrants().filterNot { it == READ_MEDIA_VISUAL_USER_SELECTED }

    internal fun AppGrant.toAndroidGrants(): List<String> {
        return when (this) {
            AppGrant.CAMERA -> listOf(Manifest.permission.CAMERA)
            AppGrant.GALLERY, AppGrant.STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            // Save-only media access. Scoped storage (API 29+) lets an app insert into its own
            // MediaStore collections with no permission at all, so the list is empty there and
            // checkStatus reports GRANTED without ever showing a prompt. Only API 26-28 needs
            // WRITE_EXTERNAL_STORAGE. minSdk is 26, so no branch below that is required.
            AppGrant.GALLERY_ADD_ONLY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) emptyList()
                else listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            AppGrant.GALLERY_IMAGES_ONLY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 
                    listOf(Manifest.permission.READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    listOf(Manifest.permission.READ_MEDIA_IMAGES)
                else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            AppGrant.GALLERY_VIDEO_ONLY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.READ_MEDIA_VIDEO)
                else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            AppGrant.LOCATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            AppGrant.LOCATION_ALWAYS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val hasForeground = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (hasForeground) listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    else listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            AppGrant.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            AppGrant.SCHEDULE_EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.SCHEDULE_EXACT_ALARM) else emptyList()
            AppGrant.BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            AppGrant.BLUETOOTH_ADVERTISE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_ADVERTISE) else emptyList()
            AppGrant.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
            AppGrant.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            AppGrant.READ_CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
            AppGrant.CALENDAR -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            AppGrant.READ_CALENDAR -> listOf(Manifest.permission.READ_CALENDAR)
            AppGrant.MOTION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listOf(Manifest.permission.ACTIVITY_RECOGNITION) else listOf("com.google.android.gms.permission.ACTIVITY_RECOGNITION")
            AppGrant.NEARBY_WIFI_DEVICES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.NEARBY_WIFI_DEVICES) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            // Android 17 (API 37) local-network runtime permission. String literal + numeric
            // API check because compileSdk 36 has neither the Manifest constant nor the
            // VERSION_CODES entry — same convention as READ_MEDIA_VISUAL_USER_SELECTED above.
            //
            // Gated on the app's targetSdkVersion as well as the device's API level, because
            // the platform gates enforcement on the target: ACCESS_LOCAL_NETWORK appears only
            // in "Behavior changes: apps targeting Android 17 or higher", NOT in the "all apps"
            // list. An app targeting API 36 keeps working on the local network on an Android 17
            // device without holding the permission — and, targeting 36, has no reason to
            // declare it. Mapping it there anyway made checkSelfPermission fail for an
            // undeclared permission and escalated a working feature to DENIED_ALWAYS, sending
            // the user to Settings to find a toggle that is not listed.
            //
            // Not a rare combination: Play requires a recent targetSdk but not the newest, so
            // "targets 36, runs on 17" is the norm for roughly a year after each release.
            //
            // Either condition failing → empty list → checkStatus reports GRANTED (no-op).
            // Android has no runtime permission for cross-app tracking. The advertising ID is
            // gated by com.google.android.gms.permission.AD_ID, which is install-time (normal)
            // — auto-granted, with no dialog to show. An empty list makes checkStatus report
            // GRANTED without prompting, which is honest: the OS does not gate this at runtime.
            // Users opt out in system settings, and that surfaces as a zeroed advertising ID,
            // not as a permission denial Grant could observe.
            AppGrant.APP_TRACKING -> emptyList()
            AppGrant.LOCAL_NETWORK ->
                if (Build.VERSION.SDK_INT >= 37 && targetSdkVersion >= 37) listOf(ACCESS_LOCAL_NETWORK)
                else emptyList()
        }
    }
}
