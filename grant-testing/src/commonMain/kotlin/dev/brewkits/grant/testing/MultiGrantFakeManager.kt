package dev.brewkits.grant.testing

import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.delay

/**
 * A [GrantManager] fake that behaves *statefully* across calls, unlike [FakeGrantManager].
 *
 * Calling [request] doesn't just return a canned value — it writes the result back into the
 * status map, so a subsequent [checkStatus] for the same permission sees the post-request state.
 * This matters for tests of code that requests a permission and then immediately re-checks it
 * (e.g. a [dev.brewkits.grant.GrantGroupHandler] driving several permissions and re-evaluating
 * which ones are still outstanding), where [FakeGrantManager]'s stateless per-call return values
 * would make every re-check look identical to the first one.
 *
 * Prefer [FakeGrantManager] for most tests — reach for this one specifically when the behavior
 * under test depends on status changing as a *consequence* of a request, not just on what the
 * request returns.
 */
public class MultiGrantFakeManager : GrantManager {
    private val statusMap = mutableMapOf<GrantPermission, GrantStatus>()
    private val requestResults = mutableMapOf<GrantPermission, GrantStatus>()
    private val requestCalls = mutableSetOf<GrantPermission>()

    /** True once [openSettings] has been called. */
    public var openSettingsCalled: Boolean = false

    /** Simulates a slow platform call — [request] suspends for this long first. */
    public var delayMillis: Long = 0

    /** Total number of times [request] has resolved, across every permission. */
    public var requestCount: Int = 0

    /** Sets what [checkStatus] returns for [grant] until a [request] changes it. */
    public fun setStatus(grant: GrantPermission, status: GrantStatus) {
        statusMap[grant] = status
    }

    /** Sets what [request] resolves to for [grant] — [checkStatus] reflects this afterward too. */
    public fun setRequestResult(grant: GrantPermission, result: GrantStatus) {
        requestResults[grant] = result
    }

    /** Sets both the pre-request status and the request outcome for [grant] in one call. */
    public fun configure(grant: GrantPermission, status: GrantStatus, requestResult: GrantStatus = status) {
        statusMap[grant] = status
        requestResults[grant] = requestResult
    }

    /** True if [request] has been called for [grant] at least once. */
    public fun isRequestCalled(grant: GrantPermission): Boolean = requestCalls.contains(grant)

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        return statusMap[grant] ?: GrantStatus.NOT_DETERMINED
    }

    override suspend fun request(grant: GrantPermission): GrantStatus {
        requestCount++
        if (delayMillis > 0) {
            delay(delayMillis)
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

    override fun setLauncher(launcher: GrantLauncher) {
        // No-op for fake
    }
}
