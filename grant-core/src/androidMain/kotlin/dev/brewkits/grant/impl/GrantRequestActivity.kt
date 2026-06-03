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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

class GrantRequestViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private const val KEY_ALREADY_LAUNCHED = "already_launched"
        private const val KEY_REQUEST_ID = "request_id"
    }

    var alreadyLaunched: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_ALREADY_LAUNCHED) ?: false
        set(value) {
            savedStateHandle[KEY_ALREADY_LAUNCHED] = value
        }

    var requestId: String?
        get() = savedStateHandle.get<String>(KEY_REQUEST_ID)
        set(value) {
            savedStateHandle[KEY_REQUEST_ID] = value
        }
}

/**
 * Transparent Activity for handling runtime grant requests.
 */
class GrantRequestActivity : ComponentActivity() {

    private var requestMultipleGrantsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var currentGrants = arrayOf<String>()
    private val viewModel: GrantRequestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure the static guard is set even if recreated by OS after process death
        isActivityActive.set(true)
        lastActivityLaunchTime = System.currentTimeMillis()

        try {
            val requestId = viewModel.requestId 
                ?: intent.getStringExtra(EXTRA_REQUEST_ID)
                ?: ""

            if (requestId.isEmpty()) {
                GrantLogger.w(TAG, "No requestId found - finishing activity")
                finishAndCleanup()
                return
            }
            
            viewModel.requestId = requestId

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

            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (!isChangingConfigurations) {
                        val rid = viewModel.requestId ?: ""
                        val deferred = pendingResults[rid]
                        if (deferred?.isActive == true) {
                            setResult(rid, GrantResult.ERROR)
                            pendingResults.remove(rid)
                            pendingTimestamps.remove(rid)
                        }
                        isActivityActive.set(false)
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
                // Set the flag AFTER launch() so that a crash in launch() doesn't leave
                // alreadyLaunched=true with no launcher ever fired — which would hang
                // the request forever on process-death restoration.
                requestMultipleGrantsLauncher?.launch(currentGrants)
                viewModel.alreadyLaunched = true
            }
        } catch (e: Exception) {
            GrantLogger.e(TAG, "Error in onCreate: ${e.message}", e)
            val rid = viewModel.requestId ?: ""
            if (rid.isNotEmpty()) {
                setResult(rid, GrantResult.ERROR)
            }
            finishAndCleanup()
        }
    }

    private fun finishAndCleanup() {
        isActivityActive.set(false)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun setResult(requestId: String, result: GrantResult) {
        pendingResults[requestId]?.complete(result)
    }

    companion object {
        private const val TAG = "GrantRequestActivity"
        private const val EXTRA_GRANTS = "grants"
        private const val EXTRA_REQUEST_ID = "request_id"

        private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<GrantResult>>()
        private val pendingTimestamps = ConcurrentHashMap<String, Long>()
        
        // Increase cleanup threshold to handle slow devices or long rationale reading
        private const val ORPHAN_CLEANUP_THRESHOLD_MS = 300_000L // 5 minutes

        private val isActivityActive = AtomicBoolean(false)
        private var lastActivityLaunchTime = 0L

        /**
         * Check if any GrantRequestActivity is currently active.
         */
        fun isAnyActivityActive(): Boolean = isActivityActive.get()

        /**
         * Launch this Activity to request one or more grants.
         */
        fun requestGrants(context: Context, androidGrants: List<String>): String {
            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val appContext = context.applicationContext

            pendingResults[requestId] = CompletableDeferred()
            pendingTimestamps[requestId] = now

            cleanupOrphanedEntries()

            // Apply timeout reset before CAS to handle stuck guard state
            if (isActivityActive.get() && (now - lastActivityLaunchTime > 60_000L)) {
                GrantLogger.w(TAG, "Activity guard reset after 60s timeout.")
                isActivityActive.set(false)
            }

            // Atomic check-and-set: only one concurrent caller proceeds
            if (!isActivityActive.compareAndSet(false, true)) {
                GrantLogger.w(TAG, "Activity Launch Guard: Another GrantRequestActivity is already active. Yielding.")
                pendingResults[requestId]?.complete(GrantResult.ERROR)
                cleanup(requestId)
                return requestId
            }

            lastActivityLaunchTime = now

            val intent = Intent(appContext, GrantRequestActivity::class.java).apply {
                putExtra(EXTRA_GRANTS, androidGrants.toTypedArray())
                putExtra(EXTRA_REQUEST_ID, requestId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            
            try {
                appContext.startActivity(intent)
            } catch (e: Exception) {
                GrantLogger.e(TAG, "Failed to start GrantRequestActivity", e)
                isActivityActive.set(false)
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
            isActivityActive.set(false)
        }
    }

    enum class GrantResult {
        GRANTED,
        DENIED,
        DENIED_PERMANENTLY,
        ERROR
    }
}
