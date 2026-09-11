package dev.brewkits.grant

import dev.brewkits.grant.testing.FakeGrantManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one test in the codebase that proves [AppForegroundSignal]'s iOS wiring is real, not
 * just that it compiles: it posts the actual `UIApplicationDidBecomeActiveNotification` through
 * `NSNotificationCenter` — the same notification UIKit itself posts on every foreground
 * transition — rather than calling [GrantHandler.refreshStatus] directly, which would prove
 * nothing about the subscription.
 */
class GrantHandlerForegroundIosTest {

    private fun postDidBecomeActive() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = UIApplicationDidBecomeActiveNotification,
            `object` = null,
        )
    }

    @Test
    fun `autoRefreshOnForeground calls checkStatus again when the real notification fires`() = runTest {
        val manager = FakeGrantManager(mockStatus = GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)
        advanceUntilIdle() // let the init{} block's own checkStatus() land first
        val callsBeforePost = manager.checkStatusCalls.size

        val handle = handler.autoRefreshOnForeground()
        postDidBecomeActive()
        advanceUntilIdle()

        assertEquals(
            callsBeforePost + 1,
            manager.checkStatusCalls.size,
            "posting UIApplicationDidBecomeActiveNotification must trigger exactly one more " +
                "checkStatus() call via refreshStatus()",
        )
        handle.close()
    }

    @Test
    fun `closing the handle stops further refreshes`() = runTest {
        val manager = FakeGrantManager(mockStatus = GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)
        advanceUntilIdle()

        val handle = handler.autoRefreshOnForeground()
        handle.close()
        val callsAfterClose = manager.checkStatusCalls.size

        postDidBecomeActive()
        advanceUntilIdle()

        assertEquals(
            callsAfterClose,
            manager.checkStatusCalls.size,
            "no more checkStatus() calls should happen after the handle is closed",
        )
    }
}
