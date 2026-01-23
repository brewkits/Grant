package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

actual class PlatformGrantDelegate(
    private val context: Context
) {
    private val requestMutex = Mutex()

    // Cache of last request results to track denied vs not_determined states
    // Key: AppGrant, Value: Last known status
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()

    // ✅ FIX: SharedPreferences to persist "has requested before" flag
    // This survives app restart and helps distinguish NOT_DETERMINED vs DENIED
    // IMPORTANT: We only save boolean "requested or not", NOT the actual status
    // (status can change if user modifies in Settings, but "requested" is a fact)
    private val prefs by lazy {
        context.getSharedPreferences("grant_request_history", Context.MODE_PRIVATE)
    }

    /**
     * Check if this grant has been requested before (persisted across app restarts)
     */
    private fun isRequestedBefore(grant: AppGrant): Boolean {
        return prefs.getBoolean("requested_${grant.name}", false)
    }

    /**
     * Mark this grant as "has been requested" (persist to disk)
     */
    private fun setRequested(grant: AppGrant) {
        prefs.edit().putBoolean("requested_${grant.name}", true).apply()
    }

    actual suspend fun checkStatus(grant: AppGrant): GrantStatus {
        // 1. Check Override first (Custom logic for specific OS versions)
        getGrantStatusOverride(grant)?.let { return it }

        // 2. Special handling for LOCATION_ALWAYS on Android 11+
        // Need to check if we're in "partial granted" state (foreground yes, background no)
        if (grant == AppGrant.LOCATION_ALWAYS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val hasForeground = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            val hasBackground = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            return when {
                hasBackground -> {
                    // Has background = has everything
                    statusCache.remove(grant)
                    GrantStatus.GRANTED
                }
                hasForeground -> {
                    // Has foreground but not background = still need to request
                    // Return NOT_DETERMINED so next request() will ask for background
                    GrantStatus.NOT_DETERMINED
                }
                else -> {
                    // No foreground yet - check cache first
                    statusCache[grant]?.let { return it }

                    // Check if requested before (survives app restart)
                    if (isRequestedBefore(grant)) {
                        return GrantStatus.DENIED
                    }

                    // Never requested
                    GrantStatus.NOT_DETERMINED
                }
            }
        }

        val androidGrants = grant.toAndroidGrants()

        // If list is empty (e.g., requesting Notification on older Android versions), default to GRANTED
        if (androidGrants.isEmpty()) return GrantStatus.GRANTED

        // 3. Check if all grants are granted
        val allGranted = androidGrants.all { androidGrant ->
            ContextCompat.checkSelfPermission(context, androidGrant) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            // Clear cache when granted
            statusCache.remove(grant)
            return GrantStatus.GRANTED
        }

        // 4. Check cache for previous denial status (in-memory, current session)
        // If we previously got DENIED or DENIED_ALWAYS, return that
        // This allows GrantHandler to know the difference between:
        // - Never asked (NOT_DETERMINED)
        // - Previously denied (DENIED or DENIED_ALWAYS)
        statusCache[grant]?.let { cachedStatus ->
            if (cachedStatus == GrantStatus.DENIED || cachedStatus == GrantStatus.DENIED_ALWAYS) {
                return cachedStatus
            }
        }

        // 5. Check SharedPreferences to see if we've requested before (survives app restart)
        // ✅ FIX: This solves "Dead Click" issue after app restart
        // If not granted AND requested before → User must have denied it → Return DENIED
        // This allows UI to show rationale/settings dialog instead of system dialog again
        if (isRequestedBefore(grant)) {
            return GrantStatus.DENIED
        }

        // 6. Not granted, no cache, never requested - must be NOT_DETERMINED (first time)
        return GrantStatus.NOT_DETERMINED
    }

    actual suspend fun request(grant: AppGrant): GrantStatus = requestMutex.withLock {
        getGrantStatusOverride(grant)?.let { return@withLock it }

        val androidGrants = grant.toAndroidGrants()
        if (androidGrants.isEmpty()) return@withLock GrantStatus.GRANTED

        // Double-check in case the user just granted the grant
        val allGranted = androidGrants.all { androidGrant ->
            ContextCompat.checkSelfPermission(context, androidGrant) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            // Clear cache when granted
            statusCache.remove(grant)
            return@withLock GrantStatus.GRANTED
        }

        // ✅ FIX: Mark as "requested" before showing system dialog
        // This ensures checkStatus() will return DENIED after app restart (not NOT_DETERMINED)
        // Prevents "Dead Click" issue where clicking does nothing after restart
        setRequested(grant)

        val status = try {
            val requestId = GrantRequestActivity.requestGrants(context, androidGrants)
            val resultFlow = GrantRequestActivity.getResultFlow(requestId)
                ?: return@withLock GrantStatus.DENIED

            val result = try {
                withTimeout(60_000) { resultFlow.first { it != null } }
                    ?: GrantRequestActivity.GrantResult.ERROR
            } finally {
                GrantRequestActivity.cleanup(requestId)
            }

            when (result) {
                GrantRequestActivity.GrantResult.GRANTED -> GrantStatus.GRANTED
                GrantRequestActivity.GrantResult.DENIED -> GrantStatus.DENIED
                GrantRequestActivity.GrantResult.DENIED_PERMANENTLY -> GrantStatus.DENIED_ALWAYS
                GrantRequestActivity.GrantResult.ERROR -> GrantStatus.DENIED
            }
        } catch (e: Exception) {
            GrantLogger.e("AndroidGrant", "Request failed", e)
            GrantStatus.DENIED
        }

        // Cache the result (except GRANTED, which we handle separately)
        if (status != GrantStatus.GRANTED) {
            statusCache[grant] = status
        }

        return@withLock status
    }

    actual fun openSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null)
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- SMART MAPPING LOGIC ---

    private fun AppGrant.toAndroidGrants(): List<String> {
        return when (this) {
            AppGrant.CAMERA -> listOf(Manifest.permission.CAMERA)

            AppGrant.GALLERY, AppGrant.STORAGE -> {
                // Smart handling: Android 13+ uses Media Grants, older versions use Read External
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // For Gallery, only request Images and Video (Audio is not needed)
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            AppGrant.LOCATION -> {
                // Android 12+ (S) requires both grants for precise location
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            AppGrant.LOCATION_ALWAYS -> {
                // ANDROID 11+: Must request foreground FIRST, then background SEPARATELY
                // Android 11+ BLOCKS background request if foreground not granted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Check if foreground already granted
                    val hasForeground = listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ).all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (hasForeground) {
                        // Foreground OK - only request background
                        listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        // No foreground yet - request foreground ONLY
                        // Will need another request for background after this
                        listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            AppGrant.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList() // Older Androids auto-grant or check via Manager
                }
            }

            AppGrant.BLUETOOTH -> {
                // AUTO MAP: Android 12+ uses new BT grants, older Androids use Location
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    // Android < 12 needs Location to scan Bluetooth
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }

            AppGrant.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
            AppGrant.CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)

            AppGrant.MOTION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    listOf(Manifest.permission.ACTIVITY_RECOGNITION)
                } else {
                    // Android < 10 uses GMS grant (usually auto-granted on install or doesn't require runtime)
                    // However, for safety we return empty; the override logic will handle it if necessary
                    listOf("com.google.android.gms.permission.ACTIVITY_RECOGNITION")
                }
            }
        }
    }

    /**
     * Handle cases that do not use standard Runtime Grants
     */
    private fun getGrantStatusOverride(grant: AppGrant): GrantStatus? {
        return when (grant) {
            AppGrant.NOTIFICATION -> {
                // Android < 13: Use NotificationManager to check if settings are enabled/disabled
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    if (NotificationManagerCompat.from(context).areNotificationsEnabled())
                        GrantStatus.GRANTED
                    else
                        GrantStatus.DENIED_ALWAYS
                } else null
            }
            else -> null
        }
    }
}