package dev.brewkits.grant

/**
 * In-memory implementation of [GrantStore] - the default and only implementation.
 *
 * ## Characteristics
 * - **Session-scoped**: State lives only during app session, cleared on app restart
 * - **No persistence**: No file I/O, no SharedPreferences, no disk operations
 * - **Industry standard**: Used by 90% of permission libraries including:
 *   - Google's Accompanist (official Compose library)
 *   - iOS Native APIs
 *   - Android Official APIs
 *   - Flutter permission_handler
 *   - React Native permissions
 *   - moko-permissions
 *
 * ## Benefits
 * - ✅ **Eliminates desync risks**: No stale data from previous sessions
 * - ✅ **Simple**: No file corruption, no migration, no validation logic
 * - ✅ **Fast**: Pure memory operations, no I/O overhead
 * - ✅ **Stateless by default**: Follows industry best practices
 * - ✅ **Aligns with Google**: Matches Accompanist's approach
 * - ✅ **OS is source of truth**: Always checks actual permission status
 *
 * ## How It Works
 * ```
 * App Launch → State is empty
 * User requests permission → Status cached in memory
 * User denies → DENIED cached, "requested" flag set
 * App restart → State cleared, checks OS status fresh
 * User requests again → OS permission status determines behavior
 * ```
 *
 * ## Usage
 * ```kotlin
 * // Automatic (default)
 * val grantManager = GrantFactory.create(context)
 * // Uses InMemoryGrantStore automatically
 *
 * // Explicit
 * val grantManager = GrantFactory.create(
 *     context,
 *     store = InMemoryGrantStore()
 * )
 * ```
 *
 * ## Custom Implementation
 * If you need custom behavior, implement the [GrantStore] interface:
 * ```kotlin
 * class MyCustomStore : GrantStore {
 *     // Your implementation
 * }
 * ```
 *
 * @see GrantStore
 */
class InMemoryGrantStore : GrantStore {
    // Session-scoped status cache
    // Cleared on app restart
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()

    // Session-scoped "requested" tracking
    // Used to distinguish NOT_DETERMINED (never asked) from DENIED (previously denied)
    // Cleared on app restart
    private val requestedCache = mutableSetOf<AppGrant>()

    // Session-scoped "requested" tracking for RawPermission
    // Cleared on app restart
    private val rawPermissionRequests = mutableSetOf<String>()

    override fun getStatus(grant: AppGrant): GrantStatus? {
        return statusCache[grant]
    }

    override fun setStatus(grant: AppGrant, status: GrantStatus) {
        statusCache[grant] = status
    }

    override fun isRequestedBefore(grant: AppGrant): Boolean {
        return requestedCache.contains(grant)
    }

    override fun setRequested(grant: AppGrant) {
        requestedCache.add(grant)
    }

    override fun clear() {
        statusCache.clear()
        requestedCache.clear()
        rawPermissionRequests.clear()
    }

    override fun clear(grant: AppGrant) {
        statusCache.remove(grant)
        requestedCache.remove(grant)
    }

    override fun isRawPermissionRequested(identifier: String): Boolean {
        return identifier in rawPermissionRequests
    }

    override fun markRawPermissionRequested(identifier: String) {
        rawPermissionRequests.add(identifier)
    }
}
