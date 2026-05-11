package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for Issue #32: `IllegalStateException: This mutex is not locked` thrown
 * from `GrantHandler.request()` when the same permission was requested twice and was
 * already granted on the first call.
 *
 * Root cause (v1.3.1)
 * -------------------
 * `request()`, `onRationaleConfirmed()`, `requestSuspend()` and `requestWithCustomUi()`
 * all used the same pattern:
 *
 *     if (!requestMutex.tryLock()) return
 *     val job = scope.launch { try { ... } finally { unlock() } }
 *     if (!job.isActive) unlock()    // ← second unlock
 *
 * When the coroutine body executed synchronously (typical for GRANTED/PARTIAL_GRANTED
 * paths that never suspend), the `finally` block unlocked the mutex before the outer
 * `if (!job.isActive)` check ran. By that point the job was no longer active, so the
 * mutex was unlocked a second time and threw.
 *
 * Fix (v1.4.0)
 * ------------
 * All four entry points now acquire the mutex *inside* the launched coroutine so the
 * acquire/release lifecycle is fully contained within a single coroutine. There is no
 * outer fallback unlock that can race with the finally block.
 */
class Issue32MutexDoubleUnlockTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `Issue-32 - request twice when GRANTED does not throw`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.NOTIFICATION, this)

        var invocations = 0
        handler.request { invocations++ }
        advanceUntilIdle()
        handler.request { invocations++ }
        advanceUntilIdle()

        assertEquals(2, invocations, "Both callbacks should fire without IllegalStateException")
    }

    @Test
    fun `Issue-32 - request twice when PARTIAL_GRANTED does not throw`() = testScope.runTest {
        manager.mockStatus = GrantStatus.PARTIAL_GRANTED
        manager.mockRequestResult = GrantStatus.PARTIAL_GRANTED
        val handler = GrantHandler(manager, AppGrant.GALLERY, this)

        var invocations = 0
        handler.request { invocations++ }
        advanceUntilIdle()
        handler.request { invocations++ }
        advanceUntilIdle()

        assertEquals(2, invocations, "Both callbacks should fire without IllegalStateException")
    }

    @Test
    fun `Issue-32 - requestSuspend twice when GRANTED does not throw`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.NOTIFICATION, this)

        val first = handler.requestSuspend()
        val second = handler.requestSuspend()

        assertEquals(GrantStatus.GRANTED, first)
        assertEquals(GrantStatus.GRANTED, second)
    }

    @Test
    fun `Issue-32 - requestWithCustomUi twice when GRANTED does not throw`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.NOTIFICATION, this)

        var invocations = 0
        repeat(2) {
            handler.requestWithCustomUi(
                onShowRationale = { _, _, _ -> },
                onShowSettings = { _, _, _ -> },
                onGranted = { invocations++ }
            )
            advanceUntilIdle()
        }

        assertEquals(2, invocations)
    }
}
