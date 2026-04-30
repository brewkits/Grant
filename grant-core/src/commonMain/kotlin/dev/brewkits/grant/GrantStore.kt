package dev.brewkits.grant

/**
 * An abstraction for tracking the history of permission requests.
 *
 * Grant uses an **in-memory storage** strategy by default, which is the industry
 * standard for mobile permission libraries. This ensures that the OS native
 * status remains the source of truth while providing enough session context
 * to differentiate between "Never Asked" and "Denied Always" on Android.
 */
interface GrantStore {
    /**
     * Retrieves the session-cached status for a permission.
     */
    fun getStatus(grant: AppGrant): GrantStatus?

    /**
     * Caches the status for a permission in the current session.
     */
    fun setStatus(grant: AppGrant, status: GrantStatus)

    /**
     * Checks if a permission has been requested during this app session or install.
     */
    fun isRequestedBefore(grant: AppGrant): Boolean

    /**
     * Marks a permission as having been requested.
     */
    fun setRequested(grant: AppGrant)

    /**
     * Clears all session-cached data.
     */
    fun clear()

    /**
     * Clears session-cached data for a specific permission.
     */
    fun clear(grant: AppGrant)

    /**
     * Checks if a custom [RawPermission] has been requested.
     */
    fun isRawPermissionRequested(identifier: String): Boolean

    /**
     * Marks a custom [RawPermission] as having been requested.
     */
    fun markRawPermissionRequested(identifier: String)
}

/**
 * Extension for checking if any [GrantPermission] has been requested before.
 */
fun GrantStore.isRequestedBefore(grant: GrantPermission): Boolean = when (grant) {
    is AppGrant     -> isRequestedBefore(grant)
    is RawPermission -> isRawPermissionRequested(grant.identifier)
}

/**
 * Extension for marking any [GrantPermission] as requested.
 */
fun GrantStore.setRequested(grant: GrantPermission) = when (grant) {
    is AppGrant      -> setRequested(grant)
    is RawPermission -> markRawPermissionRequested(grant.identifier)
}

/**
 * Extension for getting the status of any [GrantPermission].
 */
fun GrantStore.getStatus(grant: GrantPermission): GrantStatus? = when (grant) {
    is AppGrant      -> getStatus(grant)
    is RawPermission -> null
}
