package dev.brewkits.grant.fakes

import dev.brewkits.grant.*

internal class FakeGrantStore : GrantStore {
    private val requestedSet = mutableSetOf<String>()
    private val statusMap = mutableMapOf<String, GrantStatus>()

    override fun getStatus(grant: AppGrant): GrantStatus? = statusMap[grant.name]
    override fun setStatus(grant: AppGrant, status: GrantStatus) { statusMap[grant.name] = status }
    override fun isRequestedBefore(grant: AppGrant): Boolean = requestedSet.contains(grant.name)
    override fun setRequested(grant: AppGrant) { requestedSet.add(grant.name) }
    override fun clear() { requestedSet.clear(); statusMap.clear() }
    override fun clear(grant: AppGrant) { requestedSet.remove(grant.name); statusMap.remove(grant.name) }
    override fun isRawPermissionRequested(identifier: String): Boolean = requestedSet.contains(identifier)
    override fun markRawPermissionRequested(identifier: String) { requestedSet.add(identifier) }
}
