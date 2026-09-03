package dev.brewkits.grant

import android.content.Context
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
class SharedPreferencesGrantStore(context: Context) : GrantStore {

    private val appContext = context.applicationContext

    private val prefs = appContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        discardHistoryFromOtherInstalls()
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
    private fun discardHistoryFromOtherInstalls() {
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
