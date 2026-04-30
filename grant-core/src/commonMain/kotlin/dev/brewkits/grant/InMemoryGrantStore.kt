package dev.brewkits.grant

import dev.brewkits.grant.utils.withLock

/**
 * The default implementation of [GrantStore] that persists state in-memory.
 */
class InMemoryGrantStore : GrantStore {
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()
    private val requestedCache = mutableSetOf<AppGrant>()
    private val rawPermissionRequests = mutableSetOf<String>()
    private val lock = dev.brewkits.grant.utils.PlatformLock()

    override fun getStatus(grant: AppGrant): GrantStatus? = lock.withLock {
        statusCache[grant]
    }

    override fun setStatus(grant: AppGrant, status: GrantStatus) {
        lock.withLock {
            statusCache[grant] = status
        }
    }

    override fun isRequestedBefore(grant: AppGrant): Boolean = lock.withLock {
        requestedCache.contains(grant)
    }

    override fun setRequested(grant: AppGrant) {
        lock.withLock {
            requestedCache.add(grant)
        }
    }

    override fun clear() {
        lock.withLock {
            statusCache.clear()
            requestedCache.clear()
            rawPermissionRequests.clear()
        }
    }

    override fun clear(grant: AppGrant) {
        lock.withLock {
            statusCache.remove(grant)
            requestedCache.remove(grant)
        }
    }

    override fun isRawPermissionRequested(identifier: String): Boolean = lock.withLock {
        identifier in rawPermissionRequests
    }

    override fun markRawPermissionRequested(identifier: String) {
        lock.withLock {
            rawPermissionRequests.add(identifier)
        }
    }
}
