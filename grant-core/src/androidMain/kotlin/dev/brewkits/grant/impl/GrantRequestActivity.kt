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
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

public class GrantRequestViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    public companion object {
        private const val KEY_ALREADY_LAUNCHED = "already_launched"
        private const val KEY_REQUEST_ID = "request_id"
    }

    public var alreadyLaunched: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_ALREADY_LAUNCHED) ?: false
        set(value) {
            savedStateHandle[KEY_ALREADY_LAUNCHED] = value
        }

    public var requestId: String?
        get() = savedStateHandle.get<String>(KEY_REQUEST_ID)
        set(value) {
            savedStateHandle[KEY_REQUEST_ID] = value
        }
}

/**
 * Transparent Activity for handling runtime grant requests.
 */
public class GrantRequestActivity : ComponentActivity() {

    private var requestMultipleGrantsLauncher: ActivityResultLauncher<Array<String>>? = null
    private var currentGrants = arrayOf<String>()
    private val viewModel: GrantRequestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            // Claim the guard for THIS request. Normally requestGrants() already set it to
            // this same id, and the CAS is a no-op. After process death the static guard is
            // gone, so this re-establishes ownership for the restored Activity — which is why
            // it is done here, with the id in hand, rather than unconditionally above.
            guardOwner.compareAndSet(null, requestId)
            lastActivityLaunchTime = System.currentTimeMillis()

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
                            // Complete so a caller awaiting this never hangs, but leave the
                            // entry: the caller's own finally-block calls cleanup(rid).
                            setResult(rid, GrantResult.ERROR)
                        }
                        // Release only if this Activity's request owns the guard.
                        guardOwner.compareAndSet(rid, null)
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
        // Release only this request's own claim; a blind clear here would free a guard held
        // by a different, still-live request (see the KDoc on guardOwner).
        viewModel.requestId?.let { guardOwner.compareAndSet(it, null) }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun setResult(requestId: String, result: GrantResult) {
        pendingResults[requestId]?.complete(result)
    }

    public companion object {
        private const val TAG = "GrantRequestActivity"
        private const val EXTRA_GRANTS = "grants"
        private const val EXTRA_REQUEST_ID = "request_id"

        private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<GrantResult>>()
        private val pendingTimestamps = ConcurrentHashMap<String, Long>()
        
        // Increase cleanup threshold to handle slow devices or long rationale reading
        private const val ORPHAN_CLEANUP_THRESHOLD_MS = 300_000L // 5 minutes

        /** How long a guard may be held before a new request may take it as stale. */
        private const val GUARD_STALE_AFTER_MS = 60_000L

        /**
         * The request that currently owns the single-Activity guard, or `null` when free.
         *
         * An `AtomicBoolean` was not enough: [cleanup] is called by every request, including
         * one that *lost* the race, and an unowned flag let that loser clear the winner's
         * guard while the winner's system dialog was still on screen. A second Activity could
         * then launch over the first, and — worse — the loser's own entry was removed before
         * its caller could await it, so `request()` returned without ever showing a dialog.
         * Storing *who* holds the guard makes release a compare-and-set instead of a blind
         * write, so only the owner can free it. Regression test:
         * `GrantRequestActivityGuardTest`.
         */
        private val guardOwner = AtomicReference<String?>(null)

        /**
         * `@Volatile`: written from [requestGrants] (any thread) and from `onCreate` (main),
         * read from [requestGrants]. Without it a stale read could satisfy the 60-second
         * staleness check below and steal the guard from a legitimately running Activity.
         */
        @Volatile
        private var lastActivityLaunchTime = 0L

        /**
         * Check if any GrantRequestActivity is currently active.
         */
        public fun isAnyActivityActive(): Boolean = guardOwner.get() != null

        /**
         * Launch this Activity to request one or more grants.
         *
         * On success the returned id has a pending result the caller must await and then
         * [cleanup]. When the guard is already held, the returned id's result is completed
         * with [GrantResult.ERROR] but **left in the map**, so the caller still awaits a
         * determinate answer rather than finding nothing and returning silently.
         */
        public fun requestGrants(context: Context, androidGrants: List<String>): String {
            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val appContext = context.applicationContext

            pendingResults[requestId] = CompletableDeferred()
            pendingTimestamps[requestId] = now

            cleanupOrphanedEntries()

            // Free a guard whose owner is demonstrably stale. compareAndSet against the
            // owner we observed, so an owner that changed in the meantime is never clobbered.
            val staleOwner = guardOwner.get()
            if (staleOwner != null && (now - lastActivityLaunchTime > GUARD_STALE_AFTER_MS)) {
                GrantLogger.w(TAG, "Activity guard reset after ${GUARD_STALE_AFTER_MS}ms timeout.")
                guardOwner.compareAndSet(staleOwner, null)
            }

            // Atomic claim: only one concurrent caller becomes the owner.
            if (!guardOwner.compareAndSet(null, requestId)) {
                GrantLogger.w(TAG, "Activity Launch Guard: Another GrantRequestActivity is already active. Yielding.")
                // Complete but do NOT remove: the caller awaits this and cleans it up itself.
                pendingResults[requestId]?.complete(GrantResult.ERROR)
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
                // Complete but leave the entry for the caller to await and clean up; only the
                // guard is released here, and only because this request owns it.
                guardOwner.compareAndSet(requestId, null)
                pendingResults[requestId]?.complete(GrantResult.ERROR)
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

        /**
         * Drops this request's pending entry and releases the guard **only if this request
         * holds it**. The compare-and-set is the fix for the defect described on [guardOwner]:
         * a blind `set(false)` here let a request that never owned the guard free it out from
         * under the one that did.
         */
        internal fun cleanup(requestId: String) {
            pendingResults.remove(requestId)
            pendingTimestamps.remove(requestId)
            guardOwner.compareAndSet(requestId, null)
        }

        /**
         * Test-only escape hatch: the guard is process-wide static state, so a test that ends
         * while holding it would poison every test after it. Not part of the production flow —
         * production code releases the guard through [cleanup], which only the owner can do.
         */
        internal fun forceReleaseGuardForTest() {
            guardOwner.set(null)
            pendingResults.clear()
            pendingTimestamps.clear()
        }
    }

    public enum class GrantResult {
        GRANTED,
        DENIED,
        DENIED_PERMANENTLY,
        ERROR
    }
}
