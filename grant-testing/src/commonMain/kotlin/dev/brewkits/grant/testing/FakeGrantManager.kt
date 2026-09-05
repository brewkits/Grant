package dev.brewkits.grant.testing

import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.delay

/**
 * An in-memory [GrantManager] for tests — no platform APIs, no Activity, no real permission
 * dialog.
 *
 * [mockStatus] and [mockRequestResult] are the defaults [checkStatus]/[request] return for any
 * permission that hasn't been individually configured via [setStatus]/[setRequestResult]/
 * [configure]. Every call is recorded ([checkStatusCalls], [requestedGrants], [requestCalled],
 * [openSettingsCalled], [capturedLauncher]) so a test can assert on interactions, not just
 * return values.
 *
 * ```kotlin
 * val manager = FakeGrantManager(mockStatus = GrantStatus.DENIED)
 * manager.configure(AppGrant.CAMERA, status = GrantStatus.GRANTED)
 *
 * val handler = GrantHandler(manager, AppGrant.CAMERA, testScope)
 * handler.requestSuspend() // GRANTED — CAMERA was configured; everything else defaults to DENIED
 * ```
 *
 * This is the same fake that used to be duplicated, with small drifts, inside seven different
 * modules' own `commonTest` source sets — consolidated here so external consumers of Grant get
 * an official test double instead of writing their own, and so this project stops maintaining
 * near-identical copies of it.
 */
public class FakeGrantManager(
    public var mockStatus: GrantStatus = GrantStatus.NOT_DETERMINED,
    public var mockRequestResult: GrantStatus = GrantStatus.GRANTED,
) : GrantManager {

    private val statusMap = mutableMapOf<GrantPermission, GrantStatus>()
    private val requestResultMap = mutableMapOf<GrantPermission, GrantStatus>()

    /** When non-null, [checkStatus]/[request] throw this instead of returning. */
    public var shouldThrow: Exception? = null

    /** Simulates a slow platform call — [checkStatus]/[request] suspend for this long first. */
    public var simulatedDelayMs: Long = 0

    /** True once [request] has been called at least once, for any permission. */
    public var requestCalled: Boolean = false

    /** Every permission [request] has been called with, in call order (duplicates included). */
    public val requestedGrants: MutableList<GrantPermission> = mutableListOf()

    /** True once [openSettings] has been called. */
    public var openSettingsCalled: Boolean = false

    /** The launcher passed to [setLauncher], if any — null until a real one is captured. */
    public var capturedLauncher: GrantLauncher? = null

    /** Every permission [checkStatus] has been called with, in call order. */
    public val checkStatusCalls: MutableList<GrantPermission> = mutableListOf()

    private suspend fun simulateWork() {
        shouldThrow?.let { throw it }
        if (simulatedDelayMs > 0) delay(simulatedDelayMs)
    }

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        simulateWork()
        checkStatusCalls.add(grant)
        return statusMap[grant] ?: mockStatus
    }

    override suspend fun request(grant: GrantPermission): GrantStatus {
        simulateWork()
        requestCalled = true
        requestedGrants.add(grant)
        return requestResultMap[grant] ?: mockRequestResult
    }

    override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> {
        simulateWork()
        requestCalled = true
        requestedGrants.addAll(grants)
        return grants.associateWith { requestResultMap[it] ?: mockRequestResult }
    }

    override fun openSettings() {
        openSettingsCalled = true
    }

    override fun setLauncher(launcher: GrantLauncher) {
        capturedLauncher = launcher
    }

    /** Sets what [checkStatus] returns for [grant] specifically, overriding [mockStatus]. */
    public fun setStatus(grant: GrantPermission, status: GrantStatus) {
        statusMap[grant] = status
    }

    /** Sets what [request] returns for [grant] specifically, overriding [mockRequestResult]. */
    public fun setRequestResult(grant: GrantPermission, result: GrantStatus) {
        requestResultMap[grant] = result
    }

    /** Alias for [setRequestResult] — kept for source compatibility with pre-consolidation call sites. */
    public fun setResult(grant: GrantPermission, result: GrantStatus): Unit = setRequestResult(grant, result)

    /** Sets both [checkStatus] and [request]'s return value for [grant] in one call. */
    public fun configure(grant: GrantPermission, status: GrantStatus, requestResult: GrantStatus = status) {
        statusMap[grant] = status
        requestResultMap[grant] = requestResult
    }

    /** Clears every override and call record, and restores the constructor defaults. */
    public fun reset() {
        statusMap.clear()
        requestResultMap.clear()
        requestCalled = false
        requestedGrants.clear()
        openSettingsCalled = false
        checkStatusCalls.clear()
        capturedLauncher = null
        mockStatus = GrantStatus.NOT_DETERMINED
        mockRequestResult = GrantStatus.GRANTED
        shouldThrow = null
        simulatedDelayMs = 0
    }
}
