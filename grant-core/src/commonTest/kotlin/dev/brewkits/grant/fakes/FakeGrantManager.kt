package dev.brewkits.grant.fakes

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantStatus

/**
 * Fake GrantManager for testing without platform dependencies.
 */
class FakeGrantManager : GrantManager {
    var mockStatus: GrantStatus = GrantStatus.NOT_DETERMINED
    var mockRequestResult: GrantStatus = GrantStatus.GRANTED
    var requestCalled = false
    var openSettingsCalled = false

    override suspend fun checkStatus(grant: AppGrant): GrantStatus {
        return mockStatus
    }

    override suspend fun request(grant: AppGrant): GrantStatus {
        requestCalled = true
        return mockRequestResult
    }

    override fun openSettings() {
        openSettingsCalled = true
    }
}
