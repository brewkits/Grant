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
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

import dev.brewkits.grant.utils.ReentrantMutex

import dev.brewkits.grant.GrantLauncher

actual class PlatformGrantDelegate(
    private val context: Context,
    private val store: GrantStore
) {
    init {
        trackForegroundActivity(context)
    }

    private var launcher: GrantLauncher? = null
    actual fun setLauncher(launcher: GrantLauncher) { this.launcher = launcher }
    /**
     * Protects all map structures (mutexMapInternal, statusCacheMap).
     */
    private val mapsMutex = Mutex()
    private val mutexMapInternal = ConcurrentHashMap<String, ReentrantMutex>()

    private suspend fun getMutexFor(identifier: String): ReentrantMutex {
        return mapsMutex.withLock {
            mutexMapInternal.getOrPut(identifier) { ReentrantMutex() }
        }
    }

    // Notification status cache with timestamp (Android 12 and below)
    private var notificationStatusCache: Pair<GrantStatus, Long>? = null

    // Short-lived status cache for all grants to prevent redundant OS calls
    private val statusCacheMap = ConcurrentHashMap<String, Pair<GrantStatus, Long>>()

    companion object {
        private const val TAG = "AndroidGrantDelegate"
        private const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        // TTL for status cache. Increased for better performance,
        // but invalidated manually on every request.
        private const val STATUS_CACHE_TTL_MS = 1000L

        // Maximum time to wait for the Activity Launch Guard to clear
        private const val GUARD_CLEAR_TIMEOUT_MS = 1000L
        private const val GUARD_RETRY_INTERVAL_MS = 50L

        private const val NOTIFICATION_CACHE_TTL_MS = 200L
        private const val SYSTEM_DIALOG_TIMEOUT_MS = 300_000L // 5 minutes (matches activity cleanup)

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

    private fun isPartialGalleryAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        
        return getMutexFor(identifier).withLock {
            // Protect statusCacheMap reads through mapsMutex
            val cached = mapsMutex.withLock {
                statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
                    if (SystemClock.elapsedRealtime() - timestamp < STATUS_CACHE_TTL_MS) {
                        cachedStatus
                    } else null
                }
            }
            if (cached != null) return@withLock cached

            val status = if (grant is RawPermission) {
                val androidPermissions = grant.androidPermissions
                val allGranted = androidPermissions.all { 
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
                }
                if (allGranted) GrantStatus.GRANTED
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED) {
                    GrantStatus.PARTIAL_GRANTED
                } else {
                    // shouldShowRequestPermissionRationale() is OS-persisted and survives process
                    // death, unlike store.isRawPermissionRequested(). Check it first so a soft
                    // denial is still detected after an app restart (Issue #55).
                    val activeActivity = PlatformConfig.activity ?: (context as? android.app.Activity)
                    val anyCanShowRationale = activeActivity != null &&
                        androidPermissions.any { activeActivity.shouldShowRequestPermissionRationale(it) }
                    when {
                        anyCanShowRationale -> GrantStatus.DENIED
                        store.isRawPermissionRequested(grant.identifier) -> {
                            // Fallback to DENIED to allow rationale display if activity context is missing.
                            if (activeActivity == null) GrantStatus.DENIED else GrantStatus.DENIED_ALWAYS
                        }
                        else -> GrantStatus.NOT_DETERMINED
                    }
                }
            } else {
                val appGrant = grant as AppGrant
                val overrideStatus = getGrantStatusOverride(appGrant)
                if (overrideStatus != null) {
                    overrideStatus
                } else if (appGrant == AppGrant.LOCATION_ALWAYS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val hasForeground = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    val hasBackground = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

                    // shouldShowRequestPermissionRationale() is OS-persisted and survives process
                    // death, unlike store.isRequestedBefore(). Check it first so a soft denial is
                    // still detected after an app restart (Issue #55).
                    val activeActivity = PlatformConfig.activity ?: (context as? android.app.Activity)
                    val canShowRationale = activeActivity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == true

                    when {
                        hasBackground -> GrantStatus.GRANTED
                        hasForeground -> GrantStatus.PARTIAL_GRANTED
                        canShowRationale -> GrantStatus.DENIED
                        store.isRequestedBefore(appGrant) -> if (activeActivity == null) GrantStatus.DENIED else GrantStatus.DENIED_ALWAYS
                        else -> GrantStatus.NOT_DETERMINED
                    }
                } else {
                    val androidGrants = appGrant.toAndroidGrants()
                    if (androidGrants.isEmpty()) GrantStatus.GRANTED
                    else {
                        val allGranted = androidGrants.all { 
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
                        }
                        if (allGranted) GrantStatus.GRANTED
                        else if ((appGrant == AppGrant.GALLERY || appGrant == AppGrant.GALLERY_IMAGES_ONLY || appGrant == AppGrant.GALLERY_VIDEO_ONLY) && isPartialGalleryAccessGranted()) {
                            GrantStatus.PARTIAL_GRANTED
                        } else {
                            // shouldShowRequestPermissionRationale() is the OS source of truth for the
                            // *live* DENIED vs DENIED_ALWAYS distinction, and it survives process death
                            // unlike the in-memory status cache. Consult it FIRST so an in-session
                            // transition (user denies a second time → permanent) is detected immediately
                            // instead of being masked by a stale stored DENIED (Issue #55 follow-up).
                            // Falling back to store.getStatus() before this check left checkStatus()
                            // stuck on the first denial's DENIED for the rest of the process, so the
                            // settings guide only appeared after a restart.
                            val activeActivity = PlatformConfig.activity ?: (context as? android.app.Activity)
                            val anyCanShowRationale = activeActivity != null &&
                                androidGrants.any { activeActivity.shouldShowRequestPermissionRationale(it) }
                            when {
                                anyCanShowRationale -> GrantStatus.DENIED
                                store.isRequestedBefore(appGrant) ->
                                    // Requested before and the OS won't show a rationale → permanently
                                    // denied. With no Activity to consult, fall back to the last known
                                    // stored status (or DENIED, which keeps the rationale path open).
                                    if (activeActivity == null) store.getStatus(appGrant) ?: GrantStatus.DENIED
                                    else GrantStatus.DENIED_ALWAYS
                                else -> store.getStatus(appGrant) ?: GrantStatus.NOT_DETERMINED
                            }
                        }
                    }
                }
            }

            // Protect statusCacheMap writes through mapsMutex
            mapsMutex.withLock {
                statusCacheMap[identifier] = status to SystemClock.elapsedRealtime()
            }
            status
        }
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus {
        return getMutexFor(grant.identifier).withLock {
            requestInternal(grant)
        }
    }

    actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
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
        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }

        var currentStatus = checkStatus(grant)
        if (currentStatus == GrantStatus.GRANTED) return currentStatus

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
            } catch (e: Exception) {
                GrantLogger.e(TAG, "System dialog timeout or error", e)
            }
        } else {
            // No GrantLauncher registered — fall back to the self-contained transparent
            // GrantRequestActivity so the system dialog still opens without requiring the
            // app to bind a launcher to its Activity/Fragment lifecycle (Issue #53).
            GrantLogger.d(TAG, "No GrantLauncher registered; using GrantRequestActivity fallback.")
            requestViaActivity(androidPermissions)
        }

        // Invalidate cache immediately after system dialog returns
        mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }

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
                    } catch (e: Exception) {
                        GrantLogger.w(TAG, "LOCATION_ALWAYS background step failed: ${e.message}")
                    } finally {
                        GrantRequestActivity.cleanup(bgRequestId)
                    }
                    mapsMutex.withLock { statusCacheMap.remove(grant.identifier) }
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
        mapsMutex.withLock {
            grants.forEach { statusCacheMap.remove(it.identifier) }
        }

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
            } catch (e: Exception) {
                GrantLogger.e("AndroidGrant", "Multi-request failed", e)
            }
        } else {
            // No GrantLauncher registered — fall back to the self-contained transparent
            // GrantRequestActivity so the system dialog still opens without lifecycle binding (Issue #53).
            GrantLogger.d(TAG, "No GrantLauncher registered; using GrantRequestActivity fallback for multi-request.")
            requestViaActivity(allAndroidPermissions.toList())
        }

        mapsMutex.withLock {
            grants.forEach { statusCacheMap.remove(it.identifier) }
        }

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
        val deferred = GrantRequestActivity.getResultDeferred(requestId) ?: return
        try {
            withTimeout(SYSTEM_DIALOG_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            GrantLogger.e(TAG, "GrantRequestActivity fallback timeout or error", e)
        } finally {
            GrantRequestActivity.cleanup(requestId)
        }
    }

    actual fun openSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            GrantLogger.e("AndroidGrant", "Failed to open settings", e)
        }
    }

    private fun getGrantStatusOverride(grant: AppGrant): GrantStatus? {
        return when (grant) {
            AppGrant.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                    if (alarmManager != null && alarmManager.canScheduleExactAlarms()) GrantStatus.GRANTED
                    else if (store.isRequestedBefore(grant)) GrantStatus.DENIED_ALWAYS else GrantStatus.NOT_DETERMINED
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
        }
    }
}
