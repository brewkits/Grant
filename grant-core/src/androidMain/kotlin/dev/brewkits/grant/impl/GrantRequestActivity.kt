package dev.brewkits.grant.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Transparent Activity for handling runtime grant requests.
 *
 * This Activity:
 * - Shows no UI (transparent theme)
 * - Requests single or multiple grants at once
 * - Returns the result via a StateFlow mapped by request ID
 * - Finishes immediately after getting the result
 * - Includes lifecycle safety to prevent memory leaks
 *
 * **Usage Pattern:**
 * 1. PlatformGrantDelegate launches this Activity with grant(s) and unique request ID
 * 2. Activity requests grant(s) and waits for user response
 * 3. Result is posted to StateFlow keyed by request ID
 * 4. Activity finishes
 * 5. PlatformGrantDelegate receives result from its specific StateFlow
 *
 * **Thread Safety (Request ID Pattern):**
 * - Each grant request gets a unique UUID
 * - Results are stored in ConcurrentHashMap keyed by request ID
 * - Prevents race conditions when multiple grants requested simultaneously
 * - Automatic cleanup after result is consumed
 *
 * **Lifecycle Safety:**
 * - Registers lifecycle observer to cleanup on destroy
 * - Prevents memory leaks from retained launcher references
 * - Clears pending results when activity is destroyed
 *
 * **Multiple Grants (Android Best Practice):**
 * - Uses RequestMultiplePermissions to show all grant dialogs together
 * - Provides better UX by grouping related grants (e.g., FINE + COARSE Location)
 * - System handles dialog flow automatically
 */
class GrantRequestActivity : ComponentActivity() {

    private var requestMultipleGrantsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var currentGrants = arrayOf<String>()
    private var requestId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Don't set any content view - keep activity completely empty
        // This prevents any UI rendering and ensures zero visual impact

        // Get request ID from savedInstanceState (process death recovery) or intent (new request)
        requestId = savedInstanceState?.getString(KEY_REQUEST_ID)
            ?: intent.getStringExtra(EXTRA_REQUEST_ID)
            ?: run {
                GrantLogger.w(TAG, "No requestId found - finishing activity")
                finish()
                return
            }

        // Check if this requestId still has a waiting coroutine
        // If process died, the old requestId has no waiting coroutine and should be abandoned
        if (!pendingResults.containsKey(requestId)) {
            GrantLogger.w(TAG, "RequestId $requestId has no pending coroutine - orphaned request after process death")
            finish()
            return
        }

        currentGrants = intent.getStringArrayExtra(EXTRA_GRANTS) ?: run {
            setResult(requestId, GrantResult.ERROR)
            finish()
            return
        }

        // Register grant launcher for multiple grants
        requestMultipleGrantsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grantsResult: Map<String, Boolean> ->
            // Determine overall result based on all grants
            val allGranted = grantsResult.values.all { it }

            val result = when {
                allGranted -> GrantResult.GRANTED
                else -> {
                    // Check if ALL denied permissions are permanently denied
                    // If at least one denied permission can still show rationale, return DENIED
                    // Only return DENIED_PERMANENTLY if ALL denied permissions are permanent
                    val deniedGrants = grantsResult.filter { !it.value }.keys

                    val rationaleStatus = deniedGrants.map { grant ->
                        val canShow = shouldShowRequestPermissionRationale(grant)
                        grant to canShow
                    }

                    val anyCanShowRationale = rationaleStatus.any { it.second }

                    if (anyCanShowRationale) {
                        GrantResult.DENIED
                    } else {
                        // All denied permissions are permanently denied
                        GrantResult.DENIED_PERMANENTLY
                    }
                }
            }

            setResult(requestId, result)
            finish()
        }

        // Add lifecycle observer for cleanup
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // Cleanup to prevent memory leaks
                requestMultipleGrantsLauncher?.unregister()
                requestMultipleGrantsLauncher = null

                // Clear pending result if activity is destroyed without result
                val flow = pendingResults[requestId]
                if (flow?.value == null) {
                    setResult(requestId, GrantResult.ERROR)
                }
            }
        })

        // Check if all grants are already granted
        val allAlreadyGranted = currentGrants.all { grant ->
            checkSelfPermission(grant) == PackageManager.PERMISSION_GRANTED
        }

        if (allAlreadyGranted) {
            setResult(requestId, GrantResult.GRANTED)
            finish()
            return
        }

        // Request the grants (all at once for better UX)
        requestMultipleGrantsLauncher?.launch(currentGrants)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save requestId to survive process death
        // This allows the activity to check if it's an orphaned request on recreation
        outState.putString(KEY_REQUEST_ID, requestId)
    }

    private fun setResult(requestId: String, result: GrantResult) {
        pendingResults[requestId]?.value = result
    }

    companion object {
        private const val TAG = "GrantRequestActivity"
        private const val EXTRA_GRANTS = "grants"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val KEY_REQUEST_ID = "saved_request_id"

        /**
         * Map of request ID to result StateFlow.
         * Thread-safe ConcurrentHashMap prevents race conditions when multiple
         * grants are requested simultaneously.
         */
        private val pendingResults = ConcurrentHashMap<String, MutableStateFlow<GrantResult?>>()

        /**
         * Map of request ID to creation timestamp for orphan cleanup.
         * Tracks when each request was created to identify stale entries.
         */
        private val pendingTimestamps = ConcurrentHashMap<String, Long>()

        /**
         * Threshold for cleaning up orphaned entries (2 minutes).
         * Entries older than this are considered orphaned and cleaned up.
         */
        private const val ORPHAN_CLEANUP_THRESHOLD_MS = 120_000L

        /**
         * Launch this Activity to request one or more grants.
         *
         * **Android Best Practice:**
         * - Requesting multiple related grants at once (e.g., FINE + COARSE Location)
         *   provides better UX as the system can group them into a single dialog flow
         * - This prevents showing multiple sequential dialogs for related grants
         *
         * **Process Death Recovery:**
         * - Tracks request timestamps to cleanup orphaned entries
         * - If process dies during request, old entries are cleaned up on next request
         * - Prevents memory leaks from abandoned StateFlows
         *
         * @param context Android context
         * @param androidGrants List of Android grant strings (e.g., [Manifest.permission.CAMERA])
         * @return Unique request ID to track this grant request
         */
        fun requestGrants(context: Context, androidGrants: List<String>): String {
            val requestId = UUID.randomUUID().toString()

            // Create StateFlow for this request
            pendingResults[requestId] = MutableStateFlow(null)
            pendingTimestamps[requestId] = System.currentTimeMillis()

            // Cleanup orphaned entries before starting new request
            cleanupOrphanedEntries()

            val intent = Intent(context, GrantRequestActivity::class.java).apply {
                putExtra(EXTRA_GRANTS, androidGrants.toTypedArray())
                putExtra(EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(intent)

            return requestId
        }

        /**
         * Cleanup orphaned entries that have exceeded the timeout threshold.
         * Called automatically on each new request to prevent memory leaks.
         *
         * **Why This is Needed:**
         * - Process death leaves orphaned entries in pendingResults
         * - Old requestId has no waiting coroutine after process death
         * - Orphaned StateFlows consume memory (~200 bytes each)
         * - This cleanup prevents unbounded memory growth
         */
        private fun cleanupOrphanedEntries() {
            val now = System.currentTimeMillis()
            val orphanedIds = mutableListOf<String>()

            pendingTimestamps.entries.forEach { (requestId, timestamp) ->
                if (now - timestamp > ORPHAN_CLEANUP_THRESHOLD_MS) {
                    orphanedIds.add(requestId)
                }
            }

            orphanedIds.forEach { requestId ->
                pendingResults.remove(requestId)
                pendingTimestamps.remove(requestId)
            }

            if (orphanedIds.isNotEmpty()) {
                GrantLogger.d(TAG, "Cleaned up ${orphanedIds.size} orphaned request(s)")
            }
        }

        /**
         * Get the StateFlow for a specific request ID.
         *
         * @param requestId The unique request ID returned by requestGrant()
         * @return StateFlow that will emit the result, or null if invalid request ID
         */
        internal fun getResultFlow(requestId: String): MutableStateFlow<GrantResult?>? {
            return pendingResults[requestId]
        }

        /**
         * Clean up after consuming result.
         * Call this after receiving the grant result to prevent memory leaks.
         *
         * @param requestId The unique request ID to clean up
         */
        internal fun cleanup(requestId: String) {
            pendingResults.remove(requestId)
            pendingTimestamps.remove(requestId)
        }
    }

    enum class GrantResult {
        GRANTED,
        DENIED,
        DENIED_PERMANENTLY,
        ERROR
    }
}
