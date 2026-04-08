package dev.brewkits.grant.fakes

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus

/**
 * Fake GrantManager that supports multiple grants for group testing.
 */
class MultiGrantFakeManager : GrantManager {
    private val statusMap = mutableMapOf<GrantPermission, GrantStatus>()
    private val requestResults = mutableMapOf<GrantPermission, GrantStatus>()
    private val requestCalls = mutableSetOf<GrantPermission>()
    var openSettingsCalled = false

    fun setStatus(grant: GrantPermission, status: GrantStatus) {
        statusMap[grant] = status
    }

    fun setRequestResult(grant: GrantPermission, result: GrantStatus) {
        requestResults[grant] = result
    }

    fun isRequestCalled(grant: GrantPermission): Boolean = requestCalls.contains(grant)

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        return statusMap[grant] ?: GrantStatus.NOT_DETERMINED
    }

    override suspend fun request(grant: GrantPermission): GrantStatus {
        requestCalls.add(grant)
        return requestResults[grant] ?: GrantStatus.GRANTED
    }

    override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        val results = mutableMapOf<GrantPermission, GrantStatus>()
        for (grant in grants) {
            results[grant] = request(grant)
        }
        return results
    }

    override fun openSettings() {
        openSettingsCalled = true
    }
}
