package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

actual class PlatformGrantDelegate(
    private val context: Context,
    private val store: GrantStore
) {
    /**
     * Map of grant identifiers to Mutexes.
     * This allows requesting different permissions in parallel while still
     * protecting each individual permission from concurrent requests.
     */
    private val mutexMap = ConcurrentHashMap<String, Mutex>()

    private fun getMutexFor(identifier: String): Mutex {
        return mutexMap.getOrPut(identifier) { Mutex() }
    }

    // Notification status cache with timestamp (Android 12 and below)
    private var notificationStatusCache: Pair<GrantStatus, Long>? = null

    // Short-lived status cache for all grants to prevent redundant OS calls
    private val statusCacheMap = ConcurrentHashMap<String, Pair<GrantStatus, Long>>()

    private companion object {
        const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        const val NOTIFICATION_CACHE_TTL_MS = 5000L
        const val STATUS_CACHE_TTL_MS = 1000L
    }

    private fun isPartialGalleryAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val identifier = grant.identifier
        statusCacheMap[identifier]?.let { (cachedStatus, timestamp) ->
            if (System.currentTimeMillis() - timestamp < STATUS_CACHE_TTL_MS) {
                return cachedStatus
            }
        }

        val status = checkStatusInternal(grant)
        statusCacheMap[identifier] = status to System.currentTimeMillis()
        return status
    }

    private suspend fun checkStatusInternal(grant: GrantPermission): GrantStatus {
        if (grant is RawPermission) {
            val androidPermissions = grant.androidPermissions
            val allGranted = androidPermissions.all { 
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
            }
            if (allGranted) return GrantStatus.GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED) {
                    return GrantStatus.PARTIAL_GRANTED
                }
            }

            if (!store.isRawPermissionRequested(grant.identifier)) return GrantStatus.NOT_DETERMINED

            val isActivity = context is android.app.Activity
            val anyCanShowRationale = androidPermissions.any { 
                isActivity && (context as android.app.Activity).shouldShowRequestPermissionRationale(it) 
            }
            return if (anyCanShowRationale) GrantStatus.DENIED else GrantStatus.DENIED_ALWAYS
        }

        val appGrant = grant as AppGrant
        getGrantStatusOverride(appGrant)?.let { return it }

        if (appGrant == AppGrant.LOCATION_ALWAYS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasForeground = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            val hasBackground = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

            return when {
                hasBackground -> GrantStatus.GRANTED
                hasForeground -> GrantStatus.PARTIAL_GRANTED
                store.isRequestedBefore(appGrant) -> GrantStatus.DENIED
                else -> GrantStatus.NOT_DETERMINED
            }
        }

        val androidGrants = appGrant.toAndroidGrants()
        if (androidGrants.isEmpty()) return GrantStatus.GRANTED

        val allGranted = androidGrants.all { 
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }
        if (allGranted) return GrantStatus.GRANTED

        if (appGrant == AppGrant.GALLERY || appGrant == AppGrant.GALLERY_IMAGES_ONLY || appGrant == AppGrant.GALLERY_VIDEO_ONLY) {
            if (isPartialGalleryAccessGranted()) return GrantStatus.PARTIAL_GRANTED
        }

        store.getStatus(appGrant)?.let { if (it == GrantStatus.DENIED || it == GrantStatus.DENIED_ALWAYS) return it }
        return if (store.isRequestedBefore(appGrant)) GrantStatus.DENIED else GrantStatus.NOT_DETERMINED
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
        return lockAll(sortedGrants) {
            requestMultipleInternal(grants)
        }
    }

    private suspend fun <T> lockAll(grants: List<GrantPermission>, index: Int = 0, block: suspend () -> T): T {
        if (index >= grants.size) return block()
        return getMutexFor(grants[index].identifier).withLock {
            lockAll(grants, index + 1, block)
        }
    }

    private suspend fun requestInternal(grant: GrantPermission): GrantStatus {
        val currentStatus = checkStatus(grant)
        if (currentStatus == GrantStatus.GRANTED || currentStatus == GrantStatus.PARTIAL_GRANTED) return currentStatus

        val androidPermissions = when (grant) {
            is RawPermission -> grant.androidPermissions
            is AppGrant -> grant.toAndroidGrants()
            else -> emptyList()
        }

        if (androidPermissions.isEmpty()) return GrantStatus.GRANTED

        val requestId = GrantRequestActivity.requestGrants(context, androidPermissions)
        val deferred = GrantRequestActivity.getResultDeferred(requestId) ?: return GrantStatus.DENIED

        val result = try {
            withTimeout(60_000) { deferred.await() }
        } catch (e: Exception) {
            GrantRequestActivity.GrantResult.ERROR
        } finally {
            GrantRequestActivity.cleanup(requestId)
        }

        val finalStatus = when (result) {
            GrantRequestActivity.GrantResult.GRANTED -> GrantStatus.GRANTED
            GrantRequestActivity.GrantResult.DENIED -> GrantStatus.DENIED
            GrantRequestActivity.GrantResult.DENIED_PERMANENTLY -> GrantStatus.DENIED_ALWAYS
            else -> GrantStatus.DENIED
        }

        if (grant is AppGrant) {
            if (finalStatus == GrantStatus.GRANTED) store.clear(grant)
            else {
                store.setRequested(grant)
                store.setStatus(grant, finalStatus)
            }
        } else if (grant is RawPermission) {
            if (finalStatus != GrantStatus.GRANTED) store.markRawPermissionRequested(grant.identifier)
        }

        return finalStatus
    }

    private suspend fun requestMultipleInternal(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        val allAndroidPermissions = mutableSetOf<String>()
        grants.forEach { grant ->
            when (grant) {
                is RawPermission -> allAndroidPermissions.addAll(grant.androidPermissions)
                is AppGrant -> allAndroidPermissions.addAll(grant.toAndroidGrants())
            }
        }

        if (allAndroidPermissions.isEmpty()) return grants.associateWith { GrantStatus.GRANTED }

        try {
            val requestId = GrantRequestActivity.requestGrants(context, allAndroidPermissions.toList())
            val deferred = GrantRequestActivity.getResultDeferred(requestId)
            if (deferred != null) {
                withTimeout(60_000) { deferred.await() }
                GrantRequestActivity.cleanup(requestId)
            }
        } catch (e: Exception) {
            GrantLogger.e("AndroidGrant", "Multi-request failed", e)
        }

        return grants.associateWith { checkStatus(it) }
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
                    notificationStatusCache?.let { (status, time) -> if (System.currentTimeMillis() - time < NOTIFICATION_CACHE_TTL_MS) return status }
                    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    val status = if (enabled) GrantStatus.GRANTED else if (store.isRequestedBefore(grant)) GrantStatus.DENIED_ALWAYS else GrantStatus.NOT_DETERMINED
                    notificationStatusCache = status to System.currentTimeMillis()
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
        }
    }
}
