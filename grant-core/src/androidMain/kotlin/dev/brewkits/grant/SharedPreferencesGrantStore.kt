package dev.brewkits.grant

import android.content.Context
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
 * The backing file is excluded from backup and device-to-device transfer (see
 * `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`), so a fresh
 * install always starts with empty history and never inherits stale "already asked"
 * state from another device.
 *
 * This is the default store on Android (see `GrantFactory.create` and the Koin
 * `grantPlatformModule`). Apps that prefer the in-memory behavior can pass
 * [InMemoryGrantStore] explicitly.
 */
class SharedPreferencesGrantStore(context: Context) : GrantStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        prefs.edit().clear().apply()
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
        const val PREFS_NAME = "grant_request_history"
        const val KEY_REQUESTED_GRANTS = "requested_grants"
        const val KEY_REQUESTED_RAW = "requested_raw_permissions"
    }
}
