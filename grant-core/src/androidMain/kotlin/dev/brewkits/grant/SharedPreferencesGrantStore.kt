package dev.brewkits.grant

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.withLock

/**
 * A [GrantStore] that persists permission *request history* across process death.
 *
 * On Android, the only OS signal that survives a restart is
 * `shouldShowRequestPermissionRationale()` — and that signal is `false` for both a
 * brand-new permission and one the user has *permanently* denied. To tell those two
 * apart after the app's process has been killed, Grant needs to remember that it has
 * asked for the permission at least once. [InMemoryGrantStore] keeps that flag in RAM,
 * so it is lost on process death and the permanent-denial case is misreported as
 * `NOT_DETERMINED` — the request then silently no-ops (Issue #55).
 *
 * This store writes the "has been requested" flags to a private SharedPreferences file
 * (`grant_request_history`) so they survive restart. Only the request-history flags are
 * persisted; the per-session status cache stays in memory, because the OS remains the
 * source of truth for the *current* status.
 *
 * ### Restored data is discarded
 *
 * History that arrives from *another* installation is worse than no history: Grant would
 * believe it had already asked for permissions the OS considers untouched, and show a
 * settings guide where it should show the system dialog.
 *
 * Until 2.4.1 this was prevented with `android:fullBackupContent` /
 * `android:dataExtractionRules` on the library manifest. That was removed: a library
 * cannot set `<application>` attributes without colliding with apps that declare their own
 * backup rules, and an app resolving that collision with `tools:replace` silently dropped
 * the exclusion — so the protection was never dependable.
 *
 * The store now defends itself, independently of any app's manifest. The history is
 * stamped with the app's `firstInstallTime`, and is cleared whenever the stamp does not
 * match the current installation, or is missing entirely (data written before this
 * mechanism, which cannot be proven local). `firstInstallTime` is readable without any
 * permission and is stable across app *updates* — `lastUpdateTime` is the value that
 * moves — so ordinary updates and process death keep their history, while a cloud-backup
 * restore, device transfer, or reinstall starts clean.
 *
 * This is the default store on Android (see `GrantFactory.create` and the Koin
 * `grantPlatformModule`). Apps that prefer the in-memory behavior can pass
 * [InMemoryGrantStore] explicitly.
 */
public class SharedPreferencesGrantStore(context: Context) : GrantStore {

    private val appContext = context.applicationContext

    /**
     * Deliberately `by lazy`, and the install check runs inside it.
     *
     * Constructing this store used to do two blocking things eagerly:
     * `getSharedPreferences()` (a disk read and XML parse) and, via
     * [discardHistoryFromOtherInstalls], `PackageManager.getPackageInfo()` — a binder round
     * trip to `system_server`. Both ran on whichever thread built the object, which in
     * practice is the main thread: `GrantFactory.create(applicationContext)` is normally
     * called from `Application.onCreate()` or a ViewModel, and the Koin `single { }` is
     * resolved by the first screen that asks for it. At a 120 fps frame budget of ~8 ms, a
     * binder call alone can blow the frame, and `StrictMode.detectDiskReads()` flags it.
     *
     * Deferring to first *use* keeps object construction free. It does not make the work
     * disappear, so [warmUp] exists to pay it on a background thread before the UI
     * needs an answer; apps with a strict frame budget should call it.
     */
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { discardHistoryFromOtherInstalls(it) }
    }

    /**
     * Forces this store's disk read and `PackageManager` lookup to happen now, on the calling
     * thread, so the first real read does not pay for them.
     *
     * **Blocking — call it from a background thread**, e.g. from `Application.onCreate()`:
     * ```kotlin
     * applicationScope.launch(Dispatchers.IO) { store.warmUp() }
     * ```
     *
     * Deliberately blocking and dispatcher-free rather than taking a `CoroutineScope`: that
     * would put `kotlinx.coroutines` types into this module's public ABI, and coroutines is an
     * `implementation` dependency here. The caller already knows which scope and dispatcher it
     * wants; this only needs to be *called* from the right one.
     *
     * Optional. Skipping it is correct — the cost simply lands on whichever thread first
     * touches the store, which for an app with a tight frame budget (camera preview, games)
     * may be a frame that cannot afford it.
     */
    public fun warmUp() {
        prefs
    }

    /**
     * Clears the persisted history unless it was demonstrably written by this installation.
     *
     * Runs before any read, so restored history can never be observed.
     *
     * Three states are distinguished, and the distinction matters:
     * - **Lookup failed** (`null`): nothing can be proven, so the history is left alone.
     *   Wrongly clearing costs a real user an extra prompt; declining to clear on a failed
     *   lookup is the safer error.
     * - **No stamp present**: history written before this mechanism existed, which cannot
     *   be shown to be local — discarded.
     * - **Stamp differs**: written by another installation — discarded.
     *
     * Note that "no stamp" and "stamp of 0" are deliberately different, checked with
     * [android.content.SharedPreferences.contains] rather than a default value, because 0
     * is a legitimate `firstInstallTime` in some environments (Robolectric reports it).
     */
    private fun discardHistoryFromOtherInstalls(prefs: SharedPreferences) {
        val currentIdentity = currentInstallIdentity() ?: return

        if (prefs.contains(KEY_INSTALL_IDENTITY) &&
            prefs.getLong(KEY_INSTALL_IDENTITY, 0L) == currentIdentity
        ) {
            return
        }

        val hasHistory = prefs.getStringSet(KEY_REQUESTED_GRANTS, emptySet())?.isNotEmpty() == true ||
            prefs.getStringSet(KEY_REQUESTED_RAW, emptySet())?.isNotEmpty() == true
        if (hasHistory) {
            GrantLogger.i(
                TAG,
                "Request history was not written by this installation — discarding it so " +
                    "permissions are re-evaluated against the OS.",
            )
        }
        prefs.edit()
            .clear()
            .putLong(KEY_INSTALL_IDENTITY, currentIdentity)
            .apply()
    }

    /**
     * `firstInstallTime` for this package: unchanged by app updates ([android.content.pm.PackageInfo.lastUpdateTime]
     * is the value that moves), different on every fresh install, and readable with no
     * permission. `null` means the lookup itself failed, which is not the same as a value
     * of zero.
     */
    private fun currentInstallIdentity(): Long? = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
    } catch (e: Exception) {
        null
    }

    // Status is session state only — the OS is the source of truth for the live status,
    // so it is intentionally not persisted alongside the request history.
    //
    // NOT a performance cache, and not a duplicate of PlatformGrantDelegate's TTL-based
    // statusCacheMap despite the similar name. This one has exactly one reader: the branch in
    // checkStatus() that runs when there is no Activity available to consult
    // shouldShowRequestPermissionRationale(). There, "the last status we actually observed" is
    // the only signal left for telling DENIED from DENIED_ALWAYS. It has no TTL on purpose —
    // a stale answer beats no answer in that branch, and the OS is re-consulted as soon as an
    // Activity exists again.
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()
    private val lock = dev.brewkits.grant.utils.PlatformLock()

    override fun getStatus(grant: AppGrant): GrantStatus? = lock.withLock {
        statusCache[grant]
    }

    override fun setStatus(grant: AppGrant, status: GrantStatus) {
        lock.withLock {
            statusCache[grant] = status
        }
    }

    override fun isRequestedBefore(grant: AppGrant): Boolean =
        prefs.getStringSet(KEY_REQUESTED_GRANTS, emptySet())?.contains(grant.name) == true

    override fun setRequested(grant: AppGrant) {
        addToSet(KEY_REQUESTED_GRANTS, grant.name)
    }

    override fun isRawPermissionRequested(identifier: String): Boolean =
        prefs.getStringSet(KEY_REQUESTED_RAW, emptySet())?.contains(identifier) == true

    override fun markRawPermissionRequested(identifier: String) {
        addToSet(KEY_REQUESTED_RAW, identifier)
    }

    override fun clear() {
        lock.withLock {
            statusCache.clear()
        }
        // Re-stamp: a cleared store is still this installation's store, and dropping the
        // stamp would make the next construction treat the file as foreign.
        prefs.edit()
            .clear()
            .apply {
                currentInstallIdentity()?.let { putLong(KEY_INSTALL_IDENTITY, it) }
            }
            .apply()
    }

    override fun clear(grant: AppGrant) {
        lock.withLock {
            statusCache.remove(grant)
        }
        removeFromSet(KEY_REQUESTED_GRANTS, grant.name)
    }

    private fun addToSet(key: String, value: String) {
        lock.withLock {
            val current = prefs.getStringSet(key, emptySet()).orEmpty()
            if (value !in current) {
                prefs.edit().putStringSet(key, current + value).apply()
            }
        }
    }

    private fun removeFromSet(key: String, value: String) {
        lock.withLock {
            val current = prefs.getStringSet(key, emptySet()).orEmpty()
            if (value in current) {
                prefs.edit().putStringSet(key, current - value).apply()
            }
        }
    }

    private companion object {
        const val TAG = "SharedPreferencesGrantStore"
        const val PREFS_NAME = "grant_request_history"
        const val KEY_REQUESTED_GRANTS = "requested_grants"
        const val KEY_REQUESTED_RAW = "requested_raw_permissions"

        /** Stamp identifying the installation that wrote the history; see [discardHistoryFromOtherInstalls]. */
        const val KEY_INSTALL_IDENTITY = "install_identity"
    }
}
