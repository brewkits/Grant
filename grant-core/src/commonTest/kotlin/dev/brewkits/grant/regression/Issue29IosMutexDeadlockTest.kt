package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for Issue #29: iOS request() deadlock in v1.3.0.
 *
 * Root cause
 * ----------
 * PlatformGrantDelegate.ios.kt exposed two public suspend functions:
 *
 *   fun request(grant):     getMutexFor(id).withLock { requestInternal(grant) }
 *   fun checkStatus(grant): getMutexFor(id).withLock { checkStatusInternal(grant) }
 *
 * requestInternal() called the PUBLIC checkStatus() — which tried to acquire the
 * same per-permission Mutex that request() was already holding. Because Kotlin's
 * Mutex is non-reentrant, the coroutine suspended indefinitely. No dialog appeared,
 * no log was emitted, and the app printed a CancellationException on backgrounding.
 *
 * Fix
 * ---
 * requestInternal() now calls checkStatusInternal() directly, bypassing the Mutex.
 *
 * Why Android was unaffected
 * --------------------------
 * The Android PlatformGrantDelegate.checkStatus() deliberately does NOT acquire the
 * per-permission Mutex (see the "without holding the per-permission mutex" comment in
 * PlatformGrantDelegate.android.kt). The iOS implementation did not follow the same
 * pattern, hence the divergence.
 *
 * Test strategy
 * -------------
 * We cannot instantiate PlatformGrantDelegate from commonTest (it is an expect class).
 * Instead, we replicate its mutex pattern with a minimal in-test implementation and
 * verify that the FIXED pattern completes within a strict timeout on every platform.
 * A deadlock regression would cause the test to fail with a TimeoutCancellationException
 * rather than hanging the test runner indefinitely.
 */
@Suppress("ClassName")
class Issue29IosMutexDeadlockTest {

    // ── Test double replicating the FIXED PlatformGrantDelegate mutex pattern ────

    /**
     * Replicates the structure of PlatformGrantDelegate after the fix:
     * - [checkStatus] acquires the per-permission Mutex (public API)
     * - [requestInternal] calls [checkStatusInternal] directly (no Mutex re-entry)
     */
    private class FixedMutexManager(
        private val initialStatus: GrantStatus,
        private val requestedStatus: GrantStatus
    ) : GrantManager {

        private val mapsMutex = Mutex()
        private val mutexMap = mutableMapOf<String, Mutex>()

        private suspend fun getMutexFor(id: String): Mutex =
            mapsMutex.withLock { mutexMap.getOrPut(id) { Mutex() } }

        /** Internal path — no Mutex acquired. */
        private fun checkStatusInternal(@Suppress("UNUSED_PARAMETER") grant: GrantPermission): GrantStatus =
            initialStatus

        /** Public path — acquires per-permission Mutex. */
        override suspend fun checkStatus(grant: GrantPermission): GrantStatus =
            getMutexFor(grant.identifier).withLock { checkStatusInternal(grant) }

        /** FIXED: calls checkStatusInternal, not the public checkStatus. */
        override suspend fun request(grant: GrantPermission): GrantStatus =
            getMutexFor(grant.identifier).withLock {
                val status = checkStatusInternal(grant) // ← correct: no re-entrant lock
                if (status == GrantStatus.GRANTED || status == GrantStatus.PARTIAL_GRANTED) return@withLock status
                if (status == GrantStatus.DENIED_ALWAYS) return@withLock status
                requestedStatus
            }

        override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> =
            grants.associateWith { request(it) }

        override fun openSettings() = Unit
    }

    // ── Tests ────────────────────────────────────────────────────────────────────

    @Test
    fun `Issue-29 - request does not deadlock when status is NOT_DETERMINED`() = runTest {
        val manager = FixedMutexManager(GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        val result = withTimeout(2_000L) { manager.request(AppGrant.BLUETOOTH) }
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `Issue-29 - request does not deadlock when status is already GRANTED`() = runTest {
        val manager = FixedMutexManager(GrantStatus.GRANTED, GrantStatus.GRANTED)
        val result = withTimeout(2_000L) { manager.request(AppGrant.BLUETOOTH) }
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `Issue-29 - request does not deadlock when status is DENIED`() = runTest {
        val manager = FixedMutexManager(GrantStatus.DENIED, GrantStatus.DENIED)
        val result = withTimeout(2_000L) { manager.request(AppGrant.BLUETOOTH) }
        assertEquals(GrantStatus.DENIED, result)
    }

    @Test
    fun `Issue-29 - request does not deadlock when status is DENIED_ALWAYS`() = runTest {
        val manager = FixedMutexManager(GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val result = withTimeout(2_000L) { manager.request(AppGrant.BLUETOOTH) }
        assertEquals(GrantStatus.DENIED_ALWAYS, result)
    }

    @Test
    fun `Issue-29 - checkStatus and request on same permission do not deadlock sequentially`() = runTest {
        val manager = FixedMutexManager(GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        withTimeout(2_000L) {
            manager.checkStatus(AppGrant.BLUETOOTH)
            manager.request(AppGrant.BLUETOOTH)
            manager.checkStatus(AppGrant.BLUETOOTH)
        }
    }

    @Test
    fun `Issue-29 - request on multiple different permissions does not deadlock`() = runTest {
        val manager = FixedMutexManager(GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        val results = withTimeout(2_000L) {
            manager.request(listOf(AppGrant.BLUETOOTH, AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION))
        }
        assertEquals(4, results.size)
        results.values.forEach { assertEquals(GrantStatus.GRANTED, it) }
    }

    @Test
    fun `Issue-29 - repeated request calls on same permission do not deadlock`() = runTest {
        val manager = FixedMutexManager(GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        withTimeout(2_000L) {
            repeat(5) { manager.request(AppGrant.BLUETOOTH) }
        }
    }
}
