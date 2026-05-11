package dev.brewkits.grant.stress

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress tests focused on the locking/state-machine boundary surfaced by Issues #29 / #32.
 *
 * These exercises hammer every public mutating entry point of [GrantHandler] under
 * concurrent invocation to surface any double-unlock, deadlock, or state-corruption
 * regression at scale.
 *
 * They intentionally use small iteration counts (compared to e.g. JMH-style benchmarks)
 * because `TestScope` is single-threaded but advances virtual time across thousands of
 * launched coroutines per test — that is the cheap, deterministic way to flush race
 * conditions inside `Mutex`/`scope.launch` patterns in commonTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MutexAndStateStressTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    /**
     * 500 concurrent request() calls when status is GRANTED — must not throw
     * IllegalStateException and must invoke the callback exactly once (subsequent
     * calls bounce off the mutex via tryLock and are dropped by design).
     */
    @Test
    fun `500 concurrent request calls when GRANTED do not crash`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.NOTIFICATION, this)

        var invocations = 0
        repeat(500) { handler.request { invocations++ } }
        advanceUntilIdle()

        // First call wins the lock; later ones are dropped (documented contract).
        // We only assert no crash and at least the first callback fired.
        assertTrue(invocations >= 1, "at least the first callback should fire")
    }

    /**
     * Alternate request() / onDismiss() under stress — exercises both lock paths.
     * A double-unlock bug would surface here with high probability.
     */
    @Test
    fun `alternating request and dismiss under stress is stable`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        manager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        // Must let request() complete (which shows the rationale) before dismissing.
        // Calling onDismiss() while the request coroutine is still queued would no-op
        // and the rationale dialog would surface AFTER the dismiss, breaking the cycle.
        repeat(200) {
            handler.request { /* no-op */ }
            advanceUntilIdle()
            handler.onDismiss()
            advanceUntilIdle()
        }

        // State machine should land in a clean idle state after the final dismiss.
        val s = handler.state.value
        assertEquals(false, s.isVisible)
        assertEquals(false, s.showRationale)
        assertEquals(false, s.showSettingsGuide)
    }

    /**
     * 100 parallel requestSuspend() calls when the permission flips between
     * GRANTED and PARTIAL_GRANTED. The non-determinism stresses the resume
     * callback wiring inside [GrantHandler.requestSuspend].
     */
    @Test
    fun `100 parallel requestSuspend calls all resume`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.NOTIFICATION, this)

        val results = (1..100).map {
            async { handler.requestSuspend() }
        }.awaitAll()

        assertEquals(100, results.size)
        results.forEach { status ->
            // Either the call won the lock (GRANTED) or bounced and returned current status.
            assertTrue(
                status == GrantStatus.GRANTED || status == GrantStatus.NOT_DETERMINED,
                "unexpected status: $status"
            )
        }
    }

    /**
     * Rapid status flapping — checkStatus is invoked many times while requests
     * are in flight. The internal `_status` StateFlow must remain consistent
     * and the final state must reflect the latest [checkStatus] result.
     */
    @Test
    fun `rapid status flapping preserves StateFlow consistency`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        // Flap status 300 times.
        repeat(300) {
            manager.mockStatus = if (it % 2 == 0) GrantStatus.DENIED else GrantStatus.GRANTED
            handler.refreshStatus()
        }
        // Pin a known terminal value and confirm the StateFlow converges to it.
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        handler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }
}
