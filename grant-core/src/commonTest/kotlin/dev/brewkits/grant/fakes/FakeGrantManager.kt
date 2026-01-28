package dev.brewkits.grant.fakes

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus

/**
 * Fake GrantManager for testing without platform dependencies.
 */
class FakeGrantManager : GrantManager {
    private val statusMap = mutableMapOf<GrantPermission, GrantStatus>()
    var mockStatus: GrantStatus = GrantStatus.NOT_DETERMINED
    var mockRequestResult: GrantStatus = GrantStatus.GRANTED
    var requestCalled = false
    var openSettingsCalled = false

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        return statusMap[grant] ?: mockStatus
    }

    override suspend fun request(grant: GrantPermission): GrantStatus {
        requestCalled = true
        return mockRequestResult
    }

    override fun openSettings() {
        openSettingsCalled = true
    }

    /**
     * Helper for tests to set specific grant status
     */
    fun setStatus(grant: AppGrant, status: GrantStatus) {
        statusMap[grant] = status
    }
}
