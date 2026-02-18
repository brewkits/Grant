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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

actual class PlatformGrantDelegate(
    private val context: Context,
    private val store: GrantStore
) {
    private val requestMutex = Mutex()

    // Notification status cache with timestamp (Android 12 and below)
    // Pair: (GrantStatus, timestamp in milliseconds)
    private var notificationStatusCache: Pair<GrantStatus, Long>? = null

    // Android 14+ (API 34+) partial gallery access permission
    // This permission is granted when user selects "Select photos" instead of "Allow all"
    private companion object {
        const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        const val NOTIFICATION_CACHE_TTL_MS = 5000L // 5 seconds
    }

    /**
     * Check if user granted partial media access on Android 14+ (API 34+)
     * Partial access = user selected "Select photos" instead of "Allow all"
     * Returns true if READ_MEDIA_VISUAL_USER_SELECTED is granted
     */
    private fun hasPartialMediaAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false // Partial access only available on Android 14+
        }

        return ContextCompat.checkSelfPermission(
            context,
            READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        // Handle RawPermission (custom permissions)
        if (grant is RawPermission) {
            GrantLogger.d("AndroidGrant", "Checking RawPermission: ${grant.identifier}")
            val androidPermissions = grant.androidPermissions

            // Check all required permissions
            val allGranted = androidPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                return GrantStatus.GRANTED
            }

            // Check if we've requested before (via identifier-based tracking)
            val wasRequested = store.isRawPermissionRequested(grant.identifier)
            return if (wasRequested) {
                GrantStatus.DENIED
            } else {
                GrantStatus.NOT_DETERMINED
            }
        }

        // Cast to AppGrant (we know it's AppGrant if not RawPermission)
        val appGrant = grant as AppGrant

        // 1. Check Override first (Custom logic for specific OS versions)
        getGrantStatusOverride(appGrant)?.let { return it }

        // 2. Special handling for LOCATION_ALWAYS on Android 11+
        // Need to check if we're in "partial granted" state (foreground yes, background no)
        if (appGrant == AppGrant.LOCATION_ALWAYS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                    store.clear(appGrant)
                    GrantStatus.GRANTED
                }
                hasForeground -> {
                    // Has foreground but not background = still need to request
                    // Return NOT_DETERMINED so next request() will ask for background
                    GrantStatus.NOT_DETERMINED
                }
                else -> {
                    // No foreground yet - check cache first
                    store.getStatus(appGrant)?.let { return it }

                    // Check if requested before (behavior depends on GrantStore implementation)
                    if (store.isRequestedBefore(appGrant)) {
                        return GrantStatus.DENIED
                    }

                    // Never requested
                    GrantStatus.NOT_DETERMINED
                }
            }
        }

        val androidGrants = appGrant.toAndroidGrants()

        // If list is empty (e.g., requesting Notification on older Android versions), default to GRANTED
        if (androidGrants.isEmpty()) return GrantStatus.GRANTED

        // 3. Check if all grants are granted
        val allGranted = androidGrants.all { androidGrant ->
            ContextCompat.checkSelfPermission(context, androidGrant) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            // Clear cache when granted
            store.clear(grant)
            return GrantStatus.GRANTED
        }

        // 3.5. Android 14+ Partial Gallery Access Detection
        // If user selected "Select photos" instead of "Allow all", treat as GRANTED
        // READ_MEDIA_VISUAL_USER_SELECTED will be granted in this case
        // Note: Only applicable to image-related grants, not videos
        if ((appGrant == AppGrant.GALLERY || appGrant == AppGrant.STORAGE || appGrant == AppGrant.GALLERY_IMAGES_ONLY) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasPartialMediaAccess()) {
                GrantLogger.i(
                    "AndroidGrant",
                    "Partial media access granted for ${appGrant.name} (user selected specific photos)"
                )
                store.clear(appGrant)
                return GrantStatus.GRANTED
            }
        }

        // 4. Check cache for previous denial status
        // If we previously got DENIED or DENIED_ALWAYS, return that
        // This allows GrantHandler to know the difference between:
        // - Never asked (NOT_DETERMINED)
        // - Previously denied (DENIED or DENIED_ALWAYS)
        store.getStatus(appGrant)?.let { cachedStatus ->
            if (cachedStatus == GrantStatus.DENIED || cachedStatus == GrantStatus.DENIED_ALWAYS) {
                return cachedStatus
            }
        }

        // 5. Check if requested before (behavior depends on GrantStore implementation)
        // InMemoryGrantStore: Session-scoped, cleared on app restart
        // SharedPrefsGrantStore: Persistent across app restarts
        // If not granted AND requested before → User must have denied it → Return DENIED
        if (store.isRequestedBefore(appGrant)) {
            return GrantStatus.DENIED
        }

        // 6. Not granted, no cache, never requested - must be NOT_DETERMINED (first time)
        return GrantStatus.NOT_DETERMINED
    }

    actual suspend fun request(grant: GrantPermission): GrantStatus = requestMutex.withLock {
        // Handle RawPermission (custom permissions)
        if (grant is RawPermission) {
            GrantLogger.d("AndroidGrant", "Requesting RawPermission: ${grant.identifier}")

            // Check if already granted
            val allGranted = grant.androidPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                return@withLock GrantStatus.GRANTED
            }

            // Mark as requested before launching dialog
            store.markRawPermissionRequested(grant.identifier)

            GrantLogger.i("AndroidGrant", "Requesting RawPermission: ${grant.identifier} (permissions: ${grant.androidPermissions.joinToString()})")

            // Launch GrantRequestActivity with raw permissions
            val status = try {
                val requestId = GrantRequestActivity.requestGrants(context, grant.androidPermissions)
                val resultFlow = GrantRequestActivity.getResultFlow(requestId)
                    ?: return@withLock GrantStatus.DENIED

                val result = try {
                    withTimeout(60_000) { resultFlow.first { it != null } }
                        ?: GrantRequestActivity.GrantResult.ERROR
                } finally {
                    GrantRequestActivity.cleanup(requestId)
                }

                val status = when (result) {
                    GrantRequestActivity.GrantResult.GRANTED -> GrantStatus.GRANTED
                    GrantRequestActivity.GrantResult.DENIED -> GrantStatus.DENIED
                    GrantRequestActivity.GrantResult.DENIED_PERMANENTLY -> GrantStatus.DENIED_ALWAYS
                    GrantRequestActivity.GrantResult.ERROR -> GrantStatus.DENIED
                }

                GrantLogger.i("AndroidGrant", "RawPermission ${grant.identifier} result: $status")
                status
            } catch (e: Exception) {
                GrantLogger.e("AndroidGrant", "Request failed for RawPermission: ${grant.identifier}: ${e.message}", e)
                GrantStatus.DENIED
            }

            return@withLock status
        }

        // Cast to AppGrant
        val appGrant = grant as AppGrant

        // Invalidate notification cache before requesting (will be refreshed by getGrantStatusOverride)
        if (appGrant == AppGrant.NOTIFICATION && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            notificationStatusCache = null
            GrantLogger.i("AndroidGrant", "Cleared notification cache before request")
        }

        getGrantStatusOverride(appGrant)?.let { return@withLock it }

        val androidGrants = appGrant.toAndroidGrants()
        if (androidGrants.isEmpty()) return@withLock GrantStatus.GRANTED

        // Double-check in case the user just granted the grant
        val allGranted = androidGrants.all { androidGrant ->
            ContextCompat.checkSelfPermission(context, androidGrant) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            // Clear cache when granted
            store.clear(appGrant)
            GrantLogger.i("AndroidGrant", "Full access granted for ${appGrant.name}")
            return@withLock GrantStatus.GRANTED
        }

        // Check for partial media access on Android 14+ (before requesting)
        // Only applicable to image-related grants (not videos)
        if ((appGrant == AppGrant.GALLERY || appGrant == AppGrant.STORAGE || appGrant == AppGrant.GALLERY_IMAGES_ONLY) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPartialMediaAccess()) {
            store.clear(appGrant)
            GrantLogger.i(
                "AndroidGrant",
                "Partial media access already granted for ${appGrant.name} (user previously selected specific photos)"
            )
            return@withLock GrantStatus.GRANTED
        }

        // ✅ Mark as "requested" before showing system dialog
        // This ensures checkStatus() can return DENIED after app restart (not NOT_DETERMINED)
        // Behavior depends on GrantStore implementation (in-memory vs persistent)
        store.setRequested(appGrant)

        GrantLogger.i("AndroidGrant", "Requesting grant: ${appGrant.name} (permissions: ${androidGrants.joinToString()})")

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

            val status = when (result) {
                GrantRequestActivity.GrantResult.GRANTED -> GrantStatus.GRANTED
                GrantRequestActivity.GrantResult.DENIED -> GrantStatus.DENIED
                GrantRequestActivity.GrantResult.DENIED_PERMANENTLY -> GrantStatus.DENIED_ALWAYS
                GrantRequestActivity.GrantResult.ERROR -> GrantStatus.DENIED
            }

            GrantLogger.i("AndroidGrant", "Grant ${appGrant.name} result: $status")
            status
        } catch (e: Exception) {
            GrantLogger.e("AndroidGrant", "Request failed for ${appGrant.name}: ${e.message}", e)
            GrantStatus.DENIED
        }

        // Cache the result (except GRANTED, which we handle separately)
        if (status != GrantStatus.GRANTED) {
            store.setStatus(appGrant, status)
        }

        return@withLock status
    }

    /**
     * Opens the app's settings page where user can manually grant permissions.
     *
     * **Android Behavior:**
     * - Opens Settings > Apps > [App Name] > Permissions
     * - Intent: ACTION_APPLICATION_DETAILS_SETTINGS
     * - FLAG_ACTIVITY_NEW_TASK: Launches settings in new task
     *
     * **Error Handling:**
     * - ActivityNotFoundException: No settings app available (rare)
     * - SecurityException: System denied access (very rare)
     * - General Exception: Logs error for debugging
     */
    actual fun openSettings() {
        try {
            val packageName = context.packageName
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            GrantLogger.i("AndroidGrant", "Opening app settings for package: $packageName")
            context.startActivity(intent)
            GrantLogger.d("AndroidGrant", "Successfully launched settings activity")

        } catch (e: android.content.ActivityNotFoundException) {
            GrantLogger.e(
                "AndroidGrant",
                "Failed to open settings - ActivityNotFoundException. " +
                "This device may not have a standard Settings app.",
                e
            )
        } catch (e: SecurityException) {
            GrantLogger.e(
                "AndroidGrant",
                "Failed to open settings - SecurityException. " +
                "System denied access to settings (very rare).",
                e
            )
        } catch (e: Exception) {
            GrantLogger.e(
                "AndroidGrant",
                "Unexpected error opening settings: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
        }
    }

    // --- SMART MAPPING LOGIC ---

    internal fun AppGrant.toAndroidGrants(): List<String> {
        return when (this) {
            AppGrant.CAMERA -> listOf(Manifest.permission.CAMERA)

            AppGrant.GALLERY, AppGrant.STORAGE -> {
                // Smart handling: Android 13+ uses Media Grants, older versions use Read External
                // ⚠️ IMPORTANT: Only use this if you declared BOTH READ_MEDIA_IMAGES and READ_MEDIA_VIDEO
                // in AndroidManifest.xml. Otherwise use GALLERY_IMAGES_ONLY or GALLERY_VIDEO_ONLY.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+ (API 34+): Support partial photo access
                    // Include READ_MEDIA_VISUAL_USER_SELECTED to detect "Select photos" mode
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        READ_MEDIA_VISUAL_USER_SELECTED
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13: Standard media permissions
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            AppGrant.GALLERY_IMAGES_ONLY -> {
                // Granular permission: Images only
                // Use this if you only need image access (not videos)
                // Prevents silent denial when READ_MEDIA_VIDEO is not declared in manifest
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+: Include partial access support
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        READ_MEDIA_VISUAL_USER_SELECTED
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13: Images only
                    listOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            AppGrant.GALLERY_VIDEO_ONLY -> {
                // Granular permission: Videos only
                // Use this if you only need video access (not images)
                // Prevents silent denial when READ_MEDIA_IMAGES is not declared in manifest
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+: Videos only
                    // Note: No partial access for videos (only for images)
                    listOf(Manifest.permission.READ_MEDIA_VIDEO)
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
                // ANDROID 11+ (API 30+): MANDATORY TWO-STEP FLOW
                // Android platform requirement: Must request foreground FIRST, then background SEPARATELY
                // If you request both together, background permission is silently ignored
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Check if foreground already granted
                    val hasForeground = listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ).all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (hasForeground) {
                        // STEP 2 of 2: Foreground already granted, now request background
                        GrantLogger.i(
                            "AndroidGrant",
                            "LOCATION_ALWAYS: Step 2 of 2 - Requesting background location (foreground already granted)"
                        )
                        listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        // STEP 1 of 2: Foreground NOT granted, request foreground first
                        // User will need to call request() again after granting foreground
                        GrantLogger.i(
                            "AndroidGrant",
                            "LOCATION_ALWAYS: Step 1 of 2 - Requesting foreground location first (Android 11+ requirement)"
                        )
                        listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10 (Q): Can request both together
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    // Android 9 and below: No background permission needed
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

            AppGrant.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+ requires permission for exact alarms
                    // Note: USE_EXACT_ALARM (API 33+) is install-time, no dialog
                    // SCHEDULE_EXACT_ALARM (API 31+) requires user approval in Settings
                    listOf(Manifest.permission.SCHEDULE_EXACT_ALARM)
                } else {
                    emptyList() // Older Android versions don't require permission
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
            AppGrant.CALENDAR -> listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )

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
            AppGrant.SCHEDULE_EXACT_ALARM -> {
                // Android 12+ (API 31+): Check if app can schedule exact alarms
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE)
                        as? android.app.AlarmManager

                    if (alarmManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Use canScheduleExactAlarms() (API 31+)
                        val canSchedule = try {
                            alarmManager.canScheduleExactAlarms()
                        } catch (e: Exception) {
                            GrantLogger.e("AndroidGrant", "Error checking canScheduleExactAlarms", e)
                            false
                        }

                        if (canSchedule) {
                            GrantStatus.GRANTED
                        } else {
                            // Check if requested before to distinguish NOT_DETERMINED from DENIED
                            if (store.isRequestedBefore(grant)) {
                                GrantLogger.i(
                                    "AndroidGrant",
                                    "SCHEDULE_EXACT_ALARM denied (previously requested)"
                                )
                                GrantStatus.DENIED_ALWAYS // Must open Settings to grant
                            } else {
                                GrantLogger.i(
                                    "AndroidGrant",
                                    "SCHEDULE_EXACT_ALARM not determined (never requested)"
                                )
                                GrantStatus.NOT_DETERMINED
                            }
                        }
                    } else {
                        GrantLogger.w("AndroidGrant", "AlarmManager not available")
                        GrantStatus.DENIED_ALWAYS
                    }
                } else {
                    // Android 11 and below: No permission required
                    GrantStatus.GRANTED
                }
            }

            AppGrant.NOTIFICATION -> {
                // Android < 13: Use NotificationManager to check if notifications are enabled/disabled
                // (No runtime permission on Android 12 and below, but user can disable in Settings)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    // Check cache first (with TTL validation)
                    val cached = notificationStatusCache
                    if (cached != null) {
                        val (cachedStatus, timestamp) = cached
                        val elapsed = System.currentTimeMillis() - timestamp
                        if (elapsed < NOTIFICATION_CACHE_TTL_MS) {
                            GrantLogger.i(
                                "AndroidGrant",
                                "Using cached notification status: $cachedStatus (age: ${elapsed}ms)"
                            )
                            return cachedStatus
                        } else {
                            GrantLogger.i("AndroidGrant", "Notification cache expired (age: ${elapsed}ms)")
                        }
                    }

                    // Check actual notification settings
                    val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

                    val status = if (areNotificationsEnabled) {
                        GrantStatus.GRANTED
                    } else {
                        // Notifications are disabled - distinguish between:
                        // - NOT_DETERMINED: User never requested (first time)
                        // - DENIED_ALWAYS: User requested before, then disabled in Settings
                        if (store.isRequestedBefore(grant)) {
                            GrantLogger.i(
                                "AndroidGrant",
                                "Notifications disabled in Settings (previously requested)"
                            )
                            GrantStatus.DENIED_ALWAYS
                        } else {
                            GrantLogger.i(
                                "AndroidGrant",
                                "Notifications disabled in Settings (never requested, may be system default)"
                            )
                            GrantStatus.NOT_DETERMINED
                        }
                    }

                    // Update cache with timestamp
                    notificationStatusCache = status to System.currentTimeMillis()
                    status
                } else null
            }
            else -> null
        }
    }
}

/**
 * Helper function to convert AppGrant to Android permission strings.
 * Used by ManifestValidator and other utilities.
 *
 * @param grant The grant to convert
 * @return List of Android permission strings required for this grant
 */
internal fun toAndroidGrants(grant: AppGrant, context: Context): List<String> {
    // Create temporary delegate instance to access the extension function
    val delegate = PlatformGrantDelegate(context, dev.brewkits.grant.InMemoryGrantStore())
    return with(delegate) {
        grant.toAndroidGrants()
    }
}