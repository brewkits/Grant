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
    var delayMillis: Long = 0
    var requestCount: Int = 0

    fun setStatus(grant: GrantPermission, status: GrantStatus) {
        statusMap[grant] = status
    }

    fun setRequestResult(grant: GrantPermission, result: GrantStatus) {
        requestResults[grant] = result
    }

    fun configure(grant: GrantPermission, status: GrantStatus, requestResult: GrantStatus = status) {
        statusMap[grant] = status
        requestResults[grant] = requestResult
    }

    fun isRequestCalled(grant: GrantPermission): Boolean = requestCalls.contains(grant)

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        return statusMap[grant] ?: GrantStatus.NOT_DETERMINED
    }

    override suspend fun request(grant: GrantPermission): GrantStatus {
        requestCount++
        if (delayMillis > 0) {
            kotlinx.coroutines.delay(delayMillis)
        }
        requestCalls.add(grant)
        val result = requestResults[grant] ?: GrantStatus.GRANTED
        statusMap[grant] = result
        return result
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
