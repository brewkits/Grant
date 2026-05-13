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
        
        val pm = org.robolectric.Shadows.shadowOf(context.packageManager)
        val packageInfo = android.content.pm.PackageInfo().apply {
            packageName = context.packageName
            requestedPermissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
        pm.installPackage(packageInfo)
        
        delegate.setLauncher(object : dev.brewkits.grant.GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                // Simulate granting whatever is already in the shadow
                val app = org.robolectric.Shadows.shadowOf(context as android.app.Application)
                val results = permissions.associateWith { 
                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
                }
                onResult(results)
            }
        })

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
        val launcherCallCount = AtomicInteger(0)
        delegate.setLauncher(object : dev.brewkits.grant.GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                launcherCallCount.incrementAndGet()
                // Simulate granting whatever is already in the shadow
                val app = org.robolectric.Shadows.shadowOf(context as android.app.Application)
                val results = permissions.associateWith { 
                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
                }
                onResult(results)
            }
        })
        
        val legacyCallCount = AtomicInteger(0)

        mockkObject(GrantRequestActivity.Companion)

        // Each call returns a fresh requestId.
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            legacyCallCount.incrementAndGet()
            "test-request-id-${legacyCallCount.get()}"
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
            launcherCallCount.get() + legacyCallCount.get(),
            "Must be invoked exactly once when the call starts from PARTIAL_GRANTED. Got ${launcherCallCount.get()} launcher calls and ${legacyCallCount.get()} legacy calls."
        )

        // Sanity: status should remain PARTIAL_GRANTED since background was denied.
        assertEquals(GrantStatus.PARTIAL_GRANTED, finalStatus)
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
        val launcherCallCount = AtomicInteger(0)
        val legacyCallCount = AtomicInteger(0)

        delegate.setLauncher(object : dev.brewkits.grant.GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                launcherCallCount.incrementAndGet()
                // Step 1: Simulate granting foreground mid-flow
                app.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                app.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
                val results = permissions.associateWith { 
                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
                }
                onResult(results)
            }
        })

        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            legacyCallCount.incrementAndGet()
            "req-${legacyCallCount.get()}"
        }
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED)
            }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        delegate.request(AppGrant.LOCATION_ALWAYS)

        // 2-step flow expected: call #1 = foreground (launcher), call #2 = background (legacy).
        assertEquals(
            2,
            launcherCallCount.get() + legacyCallCount.get(),
            "Starting from NOT_DETERMINED must still trigger both steps of the " +
                "2-step LOCATION_ALWAYS flow. Got ${launcherCallCount.get()} launcher calls and ${legacyCallCount.get()} legacy calls."
        )
    }
}
