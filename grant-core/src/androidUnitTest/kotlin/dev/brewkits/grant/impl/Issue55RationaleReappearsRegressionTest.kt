package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end regression test for the Issue #55 follow-up reported on the thread: after a
 * permission is permanently denied, the **rationale** dialog reappeared instead of the
 * **settings** guide — but only in-session; an app restart behaved correctly.
 *
 * Reproduced root cause (verified by driving the real [GrantHandler]):
 * 1. The user denies twice through the rationale flow → the settings guide shows.
 * 2. The user dismisses the settings dialog (e.g. to retry via the screen's button) →
 *    [GrantHandler.onDismiss] resets `hasShownRationaleDialog` to false.
 * 3. On the next request, [PlatformGrantDelegate.checkStatus] returned a stale in-memory
 *    `DENIED` instead of `DENIED_ALWAYS` (the in-memory status cache masked the OS rationale
 *    flag), so [GrantHandler] treated it as a fresh soft denial and re-showed the rationale.
 *
 * A process restart cleared the in-memory cache while the persisted "requested before" flag
 * survived, so `checkStatus()` then correctly derived `DENIED_ALWAYS` — which is exactly why
 * the reporter saw it work only after restart.
 *
 * The delegate fix consults the OS rationale flag before the in-memory cache, so the
 * permanent denial is observed in-session. This test drives the full reporter flow through
 * [GrantHandler] against a fake that faithfully mirrors the Android delegate state machine
 * and asserts the settings guide — not the rationale — is shown on the final request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Issue55RationaleReappearsRegressionTest {

    /**
     * Faithfully mirrors [PlatformGrantDelegate]'s general AppGrant `checkStatus`/`requestInternal`
     * behavior with the fix applied: the OS rationale flag is consulted before the in-memory
     * status cache. `isRequestedBefore` is persisted; `storedStatus` is in-memory only.
     */
    private class FakeAndroidDelegate : GrantManager {
        private var osRationale = false
        private var requested = false
        private var storedStatus: GrantStatus? = null
        private var denialCount = 0

        private fun compute(): GrantStatus = when {
            osRationale -> GrantStatus.DENIED
            requested -> GrantStatus.DENIED_ALWAYS
            else -> storedStatus ?: GrantStatus.NOT_DETERMINED
        }

        override suspend fun checkStatus(grant: GrantPermission): GrantStatus = compute()

        override suspend fun request(grant: GrantPermission): GrantStatus {
            requested = true
            // The user denies; the OS only shows a prompt until the permission goes permanent.
            if (denialCount < 2) {
                denialCount++
                osRationale = (denialCount == 1) // true after 1st denial, false (permanent) after 2nd
            }
            return compute().also { if (it != GrantStatus.GRANTED) storedStatus = it }
        }

        override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> =
            grants.associateWith { request(it) }

        override fun openSettings() {}
        override fun setLauncher(launcher: GrantLauncher) {}
    }

    @Test
    fun `settings guide stays after dismiss when permanently denied - rationale does not reappear`() {
        val scope = TestScope(StandardTestDispatcher())
        scope.runTest {
            val handler = GrantHandler(FakeAndroidDelegate(), AppGrant.CAMERA, this)

            // First request → user denies (silent; user must re-initiate).
            handler.request {}; advanceUntilIdle()
            assertFalse(handler.state.value.isVisible, "first denial should not show a dialog")

            // Second request → rationale dialog.
            handler.request {}; advanceUntilIdle()
            assertTrue(handler.state.value.showRationale, "second request should show the rationale")

            // Confirm rationale → user denies a second time (now permanent) → settings guide.
            handler.onRationaleConfirmed(); advanceUntilIdle()
            assertTrue(handler.state.value.showSettingsGuide, "second denial should show the settings guide")

            // User dismisses the settings dialog (resets hasShownRationaleDialog).
            handler.onDismiss(); advanceUntilIdle()
            assertFalse(handler.state.value.isVisible, "dismiss should hide all dialogs")

            // Next request: permission is permanently denied → settings guide MUST reappear,
            // NOT the rationale (the bug). This is the user-visible assertion for Issue #55.
            handler.request {}; advanceUntilIdle()
            assertFalse(
                handler.state.value.showRationale,
                "rationale must NOT reappear once the permission is permanently denied"
            )
            assertTrue(
                handler.state.value.showSettingsGuide,
                "settings guide must be shown for a permanently-denied permission"
            )
        }
    }
}
