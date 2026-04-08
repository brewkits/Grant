package dev.brewkits.grant.fakes

import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus

/**
 * Enhanced FakeGrantManager for comprehensive testing.
 *
 * Supports per-permission status and request result overrides,
 * enabling precise control over each grant's lifecycle in tests.
 */
class FakeGrantManager : GrantManager {

    // --- Per-permission overrides (highest priority) ---
    private val statusMap = mutableMapOf<GrantPermission, GrantStatus>()
    private val requestResultMap = mutableMapOf<GrantPermission, GrantStatus>()

    // --- Global fallback defaults ---
    var mockStatus: GrantStatus = GrantStatus.NOT_DETERMINED
    var mockRequestResult: GrantStatus = GrantStatus.GRANTED

    // --- Tracking ---
    var requestCalled = false
    val requestedGrants = mutableListOf<GrantPermission>()
    var openSettingsCalled = false

    // --- Interaction recording ---
    val checkStatusCalls = mutableListOf<GrantPermission>()

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        checkStatusCalls.add(grant)
        return statusMap[grant] ?: mockStatus
    }

    override suspend fun request(grant: GrantPermission): GrantStatus {
        requestCalled = true
        requestedGrants.add(grant)
        return requestResultMap[grant] ?: mockRequestResult
    }

    override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        requestCalled = true
        requestedGrants.addAll(grants)
        return grants.associateWith { requestResultMap[it] ?: mockRequestResult }
    }

    override fun openSettings() {
        openSettingsCalled = true
    }

    // --- Configuration helpers ---

    /** Set expected status for a specific grant (used by checkStatus). */
    fun setStatus(grant: GrantPermission, status: GrantStatus) {
        statusMap[grant] = status
    }

    /** Set expected result for a specific grant (used by request). */
    fun setRequestResult(grant: GrantPermission, result: GrantStatus) {
        requestResultMap[grant] = result
    }

    /** Configure both status and request result for a grant in one call. */
    fun configure(grant: GrantPermission, status: GrantStatus, requestResult: GrantStatus = status) {
        statusMap[grant] = status
        requestResultMap[grant] = requestResult
    }

    /** Reset all state for reuse between tests. */
    fun reset() {
        statusMap.clear()
        requestResultMap.clear()
        requestCalled = false
        requestedGrants.clear()
        openSettingsCalled = false
        checkStatusCalls.clear()
        mockStatus = GrantStatus.NOT_DETERMINED
        mockRequestResult = GrantStatus.GRANTED
    }
}
