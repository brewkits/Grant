package dev.brewkits.grant.impl

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * Regression test for the v1.4.1 hotfix of Issue #33.
 *
 * Background
 * ----------
 * v1.4.0 fixed the original Issue #33 (PARTIAL_GRANTED routing). However the fix
 * introduced a regression in `PlatformGrantDelegate.android.kt#requestInternal()`:
 * when the caller invoked `request(LOCATION_ALWAYS)` while the status was already
 * `PARTIAL_GRANTED` (foreground granted, background not), the 2-step flow
 * fired a *second* background-permission request inside the same `requestInternal`
 * call. Android suppressed the duplicate request, causing the second
 * `withTimeout(60_000)` to fire — the user saw a 60-second hang before the
 * settings dialog appeared (see Issue #33 follow-up comment).
 *
 * The hotfix (commits 4c26a4c + f3a41aa) added two guards to the 2-step block:
 *
 *   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
 *       && grant == AppGrant.LOCATION_ALWAYS
 *       && finalStatus == GrantStatus.PARTIAL_GRANTED
 *       && currentStatus != GrantStatus.PARTIAL_GRANTED) {  // ← new
 *
 * The `currentStatus != PARTIAL_GRANTED` guard is the load-bearing one: it
 * skips step 2 when the caller's starting state was already PARTIAL_GRANTED
 * (i.e. the *first* request just attempted to acquire background and failed).
 *
 * What this test pins down
 * ------------------------
 * Given a system state where foreground location is GRANTED and background is
 * DENIED (currentStatus = PARTIAL_GRANTED), invoking `delegate.request(LOCATION_ALWAYS)`
 * must trigger *exactly one* `GrantRequestActivity.requestGrants` invocation, not
 * two.
 *
 * Without the hotfix, this test would observe 2 calls and time out at 60s on
 * the second `deferred.await()` (or hang the test runner). With the hotfix, the
 * test completes in milliseconds with exactly 1 call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R], manifest = Config.NONE)
class Issue33HotfixDuplicateRequestTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)

        // Set up Robolectric shadow state matching the bug scenario:
        // foreground location GRANTED, background DENIED → checkStatus returns PARTIAL_GRANTED.
        val app = shadowOf(context as Application)
        app.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        app.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        app.denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        // Sanity-check the precondition before running the actual assertion. Without
        // this, a future change to checkStatus() could make the test pass for the
        // wrong reason.
        val precondition = runBlocking { delegate.checkStatus(AppGrant.LOCATION_ALWAYS) }
        assertEquals(
            GrantStatus.PARTIAL_GRANTED,
            precondition,
            "Precondition: checkStatus must return PARTIAL_GRANTED in this shadow setup"
        )
    }

    @After
    fun tearDown() {
        unmockkObject(GrantRequestActivity.Companion)
    }

    @Test
    fun `request from PARTIAL_GRANTED state fires exactly one Activity launch, not two`() = runBlocking {
        val callCount = AtomicInteger(0)

        mockkObject(GrantRequestActivity.Companion)

        // Each call returns a fresh requestId.
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            callCount.incrementAndGet()
            "test-request-id-${callCount.get()}"
        }

        // Return a deferred that is already completed with DENIED, so
        // `deferred.await()` never blocks — without this, the test would take
        // 60 seconds per call in the no-fix path.
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED)
            }
        }

        every { GrantRequestActivity.cleanup(any()) } returns Unit

        // Exercise the path under test.
        val finalStatus = delegate.request(AppGrant.LOCATION_ALWAYS)

        // Core assertion: the duplicate request guard prevented a second launch.
        assertEquals(
            1,
            callCount.get(),
            "GrantRequestActivity.requestGrants must be invoked exactly once when " +
                "the call starts from PARTIAL_GRANTED. Got ${callCount.get()} invocations — " +
                "the v1.4.1 guard (currentStatus != PARTIAL_GRANTED) likely regressed."
        )

        // Sanity: status should remain PARTIAL_GRANTED since background was denied.
        assertEquals(GrantStatus.PARTIAL_GRANTED, finalStatus)

        // Belt-and-suspenders: explicit MockK verification.
        verify(exactly = 1) { GrantRequestActivity.requestGrants(any(), any()) }
    }

    /**
     * Counter-test: when the call starts from NOT_DETERMINED (i.e. no foreground
     * yet), the 2-step flow MUST fire — first to request foreground, second to
     * request background. This ensures the hotfix did not over-correct and break
     * the legitimate 2-step scenario.
     *
     * We deliberately keep this minimal — the goal is to prove the guard's
     * negation also works, not to re-test the entire happy path.
     */
    @Test
    fun `request from NOT_DETERMINED state still fires 2-step flow when foreground gets granted mid-flow`() = runBlocking {
        // Override the shadow state from setup(): start with NO foreground.
        val app = shadowOf(context as Application)
        app.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        app.denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        app.denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        // currentStatus is now NOT_DETERMINED (no prior request recorded in the store).
        val callCount = AtomicInteger(0)

        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            callCount.incrementAndGet()
            // After the FIRST call (foreground request), flip the shadow state so
            // checkStatus() now reports PARTIAL_GRANTED. This drives the delegate
            // into the 2-step branch.
            if (callCount.get() == 1) {
                app.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                app.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            "req-${callCount.get()}"
        }
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED)
            }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        delegate.request(AppGrant.LOCATION_ALWAYS)

        // 2-step flow expected: call #1 = foreground, call #2 = background.
        assertEquals(
            2,
            callCount.get(),
            "Starting from NOT_DETERMINED must still trigger both steps of the " +
                "2-step LOCATION_ALWAYS flow. Got ${callCount.get()} — the hotfix " +
                "may have over-restricted the guard."
        )
    }
}
