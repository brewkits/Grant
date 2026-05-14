package dev.brewkits.grant.calendar.fakes

import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus

internal class FakeGrantManager(
    var mockStatus: GrantStatus = GrantStatus.NOT_DETERMINED,
    var mockRequestResult: GrantStatus = GrantStatus.GRANTED
) : GrantManager {

    private val statusMap = mutableMapOf<GrantPermission, GrantStatus>()
    private val resultMap = mutableMapOf<GrantPermission, GrantStatus>()

    var requestCalled = false
    val requestedGrants = mutableListOf<GrantPermission>()
    var openSettingsCalled = false

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus =
        statusMap[grant] ?: mockStatus

    override suspend fun request(grant: GrantPermission): GrantStatus {
        requestCalled = true
        requestedGrants.add(grant)
        return resultMap[grant] ?: mockRequestResult
    }

    override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        requestCalled = true
        requestedGrants.addAll(grants)
        return grants.associateWith { resultMap[it] ?: mockRequestResult }
    }

    override fun openSettings() { openSettingsCalled = true }
    override fun setLauncher(launcher: GrantLauncher) {}

    fun setStatus(grant: GrantPermission, status: GrantStatus) { statusMap[grant] = status }
    fun setResult(grant: GrantPermission, result: GrantStatus) { resultMap[grant] = result }
    fun configure(grant: GrantPermission, status: GrantStatus, result: GrantStatus = status) {
        statusMap[grant] = status
        resultMap[grant] = result
    }

    fun reset() {
        statusMap.clear()
        resultMap.clear()
        requestCalled = false
        requestedGrants.clear()
        openSettingsCalled = false
        mockStatus = GrantStatus.NOT_DETERMINED
        mockRequestResult = GrantStatus.GRANTED
    }
}
