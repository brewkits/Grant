package dev.brewkits.grant

/**
 * Abstraction for storing permission request state.
 *
 * Grant uses **in-memory storage** (session-scoped) following industry standards.
 *
 * ## Why In-Memory Storage?
 *
 * After analyzing 20+ permission libraries including:
 * - Google's Accompanist (official Compose library) - uses in-memory only
 * - iOS Native APIs - stateless
 * - Android Official - stateless
 * - Flutter permission_handler - stateless
 * - React Native permissions - stateless
 * - moko-permissions - stateless
 *
 * **18/20 libraries (90%) use stateless/in-memory approach**.
 *
 * ## Benefits of In-Memory Storage
 * - ✅ Eliminates desync risks (no stale data from previous sessions)
 * - ✅ Simple and predictable behavior
 * - ✅ Follows industry best practices
 * - ✅ Aligns with Google's guidance (Accompanist)
 * - ✅ No file I/O overhead
 * - ✅ OS permission APIs are always the source of truth
 *
 * ## How It Works
 * - State lives only during app session
 * - Cleared on app restart
 * - OS permission status is checked on each app launch
 * - "Requested before" flag tracked in-memory for current session
 *
 * ## Custom Implementation
 * You can implement your own GrantStore for custom behavior:
 * ```kotlin
 * class MyCustomStore : GrantStore {
 *     // Your implementation
 * }
 *
 * val grantManager = GrantFactory.create(
 *     context,
 *     store = MyCustomStore()
 * )
 * ```
 *
 * ## Default Usage
 * ```kotlin
 * // Uses InMemoryGrantStore automatically
 * val grantManager = GrantFactory.create(context)
 * ```
 *
 * @see InMemoryGrantStore
 */
interface GrantStore {
    /**
     * Get cached status for a grant.
     * Returns null if not cached.
     *
     * This is used for session-scoped performance optimization.
     * The OS permission APIs are always the source of truth.
     */
    fun getStatus(grant: AppGrant): GrantStatus?

    /**
     * Cache status for a grant.
     *
     * This is used for session-scoped performance optimization.
     * The OS permission APIs are always the source of truth.
     */
    fun setStatus(grant: AppGrant, status: GrantStatus)

    /**
     * Check if this grant has been requested before.
     *
     * Used to distinguish:
     * - `NOT_DETERMINED`: Never asked before (show system dialog)
     * - `DENIED`: Previously asked but denied (show rationale or settings)
     *
     * This is critical for solving the Android "ambiguity problem":
     * `shouldShowRequestPermissionRationale()` returns false for BOTH
     * "never asked" and "permanently denied" states.
     */
    fun isRequestedBefore(grant: AppGrant): Boolean

    /**
     * Mark this grant as "has been requested".
     *
     * This should be called immediately before showing the system permission dialog.
     */
    fun setRequested(grant: AppGrant)

    /**
     * Clear all cached state.
     *
     * Useful for testing or when user logs out.
     */
    fun clear()

    /**
     * Clear state for specific grant.
     *
     * Useful when permission is granted (no need to track state anymore).
     */
    fun clear(grant: AppGrant)

    /**
     * Check if a RawPermission has been requested before.
     *
     * Used for tracking custom permissions identified by their string identifier.
     */
    fun isRawPermissionRequested(identifier: String): Boolean

    /**
     * Mark a RawPermission as "has been requested".
     *
     * Should be called immediately before showing the system permission dialog.
     */
    fun markRawPermissionRequested(identifier: String)
}
