package dev.brewkits.grant.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for the Activity Launch Guard.
 *
 * **The defect these pin down:** `cleanup()` used to release the guard unconditionally
 * (`isActivityActive.set(false)`), even when called by a request that had *lost* the launch
 * race and never owned it. Two consequences, both reachable by a user tapping two permission
 * buttons in quick succession:
 *
 * 1. The loser freed the winner's guard while the winner's system dialog was still on screen,
 *    letting a third request launch a second `GrantRequestActivity` over the first.
 * 2. The loser also removed its own pending entry before returning its id, so the caller's
 *    `getResultDeferred(id)` came back `null` and `requestViaActivity()` returned silently —
 *    a `request()` that never showed a dialog and never reported why.
 *
 * The guard now records *which* request holds it and releases only on a matching
 * compare-and-set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrantRequestActivityGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @AfterTest
    fun releaseGuard() {
        // The guard is process-wide static state; a test that leaves it held would poison the
        // next one. Draining every id this test class could have created is enough because
        // each test cleans up the ids it knows about.
        GrantRequestActivity.forceReleaseGuardForTest()
    }

    @Test
    fun a_losing_request_does_not_release_the_guard_held_by_the_winner() = runTest {
        val winner = GrantRequestActivity.requestGrants(context, listOf("android.permission.CAMERA"))
        assertTrue(GrantRequestActivity.isAnyActivityActive(), "winner should hold the guard")

        val loser = GrantRequestActivity.requestGrants(context, listOf("android.permission.RECORD_AUDIO"))

        // The loser cleaning up must NOT free a guard it never owned.
        GrantRequestActivity.cleanup(loser)

        assertTrue(
            GrantRequestActivity.isAnyActivityActive(),
            "guard must still be held by the winner after the loser cleaned up — " +
                "releasing it here is what allowed a second Activity to launch over the first",
        )

        // Only the owner may release it.
        GrantRequestActivity.cleanup(winner)
        assertFalse(GrantRequestActivity.isAnyActivityActive(), "owner's cleanup must release the guard")
    }

    @Test
    fun a_losing_request_still_leaves_an_awaitable_result_so_the_caller_never_returns_silently() = runTest {
        val winner = GrantRequestActivity.requestGrants(context, listOf("android.permission.CAMERA"))
        val loser = GrantRequestActivity.requestGrants(context, listOf("android.permission.RECORD_AUDIO"))

        val deferred = GrantRequestActivity.getResultDeferred(loser)
        assertNotNull(
            deferred,
            "the losing request must still have a pending entry; returning null here made " +
                "requestViaActivity() return without ever showing a dialog",
        )
        assertEquals(
            GrantRequestActivity.GrantResult.ERROR,
            deferred.await(),
            "a yielded request must resolve to a determinate ERROR, not hang",
        )

        GrantRequestActivity.cleanup(loser)
        GrantRequestActivity.cleanup(winner)
    }

    @Test
    fun guard_is_released_exactly_once_even_if_cleanup_is_called_repeatedly() = runTest {
        val owner = GrantRequestActivity.requestGrants(context, listOf("android.permission.CAMERA"))
        GrantRequestActivity.cleanup(owner)
        assertFalse(GrantRequestActivity.isAnyActivityActive())

        // A second request takes the guard; the first request's late/duplicate cleanup must
        // not steal it.
        val next = GrantRequestActivity.requestGrants(context, listOf("android.permission.RECORD_AUDIO"))
        GrantRequestActivity.cleanup(owner)

        assertTrue(
            GrantRequestActivity.isAnyActivityActive(),
            "a stale cleanup for an already-finished request must not release the new owner's guard",
        )
        GrantRequestActivity.cleanup(next)
    }
}
