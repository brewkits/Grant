package dev.brewkits.grant.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class GrantRequestViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private const val KEY_ALREADY_LAUNCHED = "already_launched"
    }

    var alreadyLaunched: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_ALREADY_LAUNCHED) ?: false
        set(value) {
            savedStateHandle[KEY_ALREADY_LAUNCHED] = value
        }
}

/**
 * Transparent Activity for handling runtime grant requests.
 */
class GrantRequestActivity : ComponentActivity() {

    private var requestMultipleGrantsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var currentGrants = arrayOf<String>()
    private var requestId = ""
    private val viewModel: GrantRequestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            requestId = savedInstanceState?.getString(KEY_REQUEST_ID)
                ?: intent.getStringExtra(EXTRA_REQUEST_ID)
                ?: ""

            if (requestId.isEmpty()) {
                GrantLogger.w(TAG, "No requestId found - finishing activity")
                finishAndCleanup()
                return
            }

            currentGrants = intent.getStringArrayExtra(EXTRA_GRANTS) ?: run {
                setResult(requestId, GrantResult.ERROR)
                finishAndCleanup()
                return
            }

            // Register launcher first
            requestMultipleGrantsLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grantsResult: Map<String, Boolean> ->
                val allGranted = grantsResult.values.all { it }
                val result = when {
                    allGranted -> GrantResult.GRANTED
                    else -> {
                        val deniedGrants = grantsResult.filter { !it.value }.keys
                        val anyCanShowRationale = deniedGrants.any { shouldShowRequestPermissionRationale(it) }
                        if (anyCanShowRationale) GrantResult.DENIED else GrantResult.DENIED_PERMANENTLY
                    }
                }
                setResult(requestId, result)
                finishAndCleanup()
            }

            // If this requestId still has a waiting coroutine, normal flow.
            // If it DOES NOT have a pending coroutine (orphaned from Process Death),
            // we STILL wait for the launcher callback so the OS can deliver the result.
            // We just don't re-launch the request if alreadyLaunched == true.
            if (!pendingResults.containsKey(requestId) && !viewModel.alreadyLaunched) {
                GrantLogger.w(TAG, "RequestId $requestId has no pending coroutine and wasn't launched - finishing orphaned activity")
                finishAndCleanup()
                return
            }

            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (!isChangingConfigurations) {
                        val deferred = pendingResults[requestId]
                        if (deferred?.isActive == true) {
                            setResult(requestId, GrantResult.ERROR)
                        }
                    }
                    requestMultipleGrantsLauncher?.unregister()
                    requestMultipleGrantsLauncher = null
                }
            })

            val allAlreadyGranted = currentGrants.all { grant ->
                checkSelfPermission(grant) == PackageManager.PERMISSION_GRANTED
            }

            if (allAlreadyGranted) {
                setResult(requestId, GrantResult.GRANTED)
                finishAndCleanup()
                return
            }

            if (!viewModel.alreadyLaunched) {
                viewModel.alreadyLaunched = true
                requestMultipleGrantsLauncher?.launch(currentGrants)
            }
        } catch (e: Exception) {
            GrantLogger.e(TAG, "Error in onCreate: ${e.message}", e)
            if (requestId.isNotEmpty()) {
                setResult(requestId, GrantResult.ERROR)
            }
            finishAndCleanup()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_REQUEST_ID, requestId)
    }

    private fun finishAndCleanup() {
        isActivityActive = false
        finish()
        // Override transition to make it completely invisible
        overridePendingTransition(0, 0)
    }

    private fun setResult(requestId: String, result: GrantResult) {
        pendingResults[requestId]?.complete(result)
    }

    companion object {
        private const val TAG = "GrantRequestActivity"
        private const val EXTRA_GRANTS = "grants"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val KEY_REQUEST_ID = "saved_request_id"

        private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<GrantResult>>()
        private val pendingTimestamps = ConcurrentHashMap<String, Long>()
        private const val ORPHAN_CLEANUP_THRESHOLD_MS = 120_000L

        @Volatile
        private var isActivityActive = false
        private var lastActivityLaunchTime = 0L

        /**
         * Launch this Activity to request one or more grants.
         */
        fun requestGrants(context: Context, androidGrants: List<String>): String {
            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            pendingResults[requestId] = CompletableDeferred()
            pendingTimestamps[requestId] = now

            cleanupOrphanedEntries()

            // Safety fallback: If isActivityActive is stuck for more than 10 seconds, reset it.
            // This prevents a permanent lock if an activity crashed or was killed without cleanup.
            if (isActivityActive && (now - lastActivityLaunchTime > 10_000L)) {
                GrantLogger.w(TAG, "Activity Launch Guard: Force resetting stuck isActivityActive flag.")
                isActivityActive = false
            }

            if (isActivityActive) {
                GrantLogger.w(TAG, "Activity Launch Guard: Another GrantRequestActivity is already active. Failing fast.")
                pendingResults[requestId]?.complete(GrantResult.ERROR)
                cleanup(requestId)
                return requestId
            }
            
            isActivityActive = true
            lastActivityLaunchTime = now

            val intent = Intent(context, GrantRequestActivity::class.java).apply {
                putExtra(EXTRA_GRANTS, androidGrants.toTypedArray())
                putExtra(EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                GrantLogger.e(TAG, "Failed to start GrantRequestActivity", e)
                isActivityActive = false
                pendingResults[requestId]?.complete(GrantResult.ERROR)
                cleanup(requestId)
            }

            return requestId
        }

        private fun cleanupOrphanedEntries() {
            val now = System.currentTimeMillis()
            val orphanedIds = mutableListOf<String>()

            pendingTimestamps.entries.forEach { (requestId, timestamp) ->
                if (now - timestamp > ORPHAN_CLEANUP_THRESHOLD_MS) {
                    orphanedIds.add(requestId)
                }
            }

            orphanedIds.forEach { requestId ->
                pendingResults[requestId]?.complete(GrantResult.ERROR)
                pendingResults.remove(requestId)
                pendingTimestamps.remove(requestId)
            }

            if (orphanedIds.isNotEmpty()) {
                GrantLogger.d(TAG, "Cleaned up ${orphanedIds.size} orphaned request(s)")
            }
        }

        internal fun getResultDeferred(requestId: String): CompletableDeferred<GrantResult>? {
            return pendingResults[requestId]
        }

        internal fun cleanup(requestId: String) {
            pendingResults.remove(requestId)
            pendingTimestamps.remove(requestId)
            isActivityActive = false
        }
    }

    enum class GrantResult {
        GRANTED,
        DENIED,
        DENIED_PERMANENTLY,
        ERROR
    }
}
