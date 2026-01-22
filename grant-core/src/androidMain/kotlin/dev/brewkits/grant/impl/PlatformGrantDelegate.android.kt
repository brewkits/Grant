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

    actual suspend fun checkStatus(grant: AppGrant): GrantStatus {
        // 1. Check Override first (Custom logic for specific OS versions)
        getGrantStatusOverride(grant)?.let { return it }

        val androidGrants = grant.toAndroidGrants()

        // If list is empty (e.g., requesting Notification on older Android versions), default to GRANTED
        if (androidGrants.isEmpty()) return GrantStatus.GRANTED

        // 2. Check standard grants
        val allGranted = androidGrants.all { androidGrant ->
            ContextCompat.checkSelfPermission(context, androidGrant) == PackageManager.PERMISSION_GRANTED
        }

        return if (allGranted) GrantStatus.GRANTED else GrantStatus.NOT_DETERMINED
    }

    actual suspend fun request(grant: AppGrant): GrantStatus = requestMutex.withLock {
        getGrantStatusOverride(grant)?.let { return@withLock it }

        val androidGrants = grant.toAndroidGrants()
        if (androidGrants.isEmpty()) return@withLock GrantStatus.GRANTED

        // Double-check in case the user just granted the grant
        val allGranted = androidGrants.all { androidGrant ->
            ContextCompat.checkSelfPermission(context, androidGrant) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) return@withLock GrantStatus.GRANTED

        return@withLock try {
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
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
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
                // ANDROID 11+ FIX: Separate Background grant to avoid system ignoring the request
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
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