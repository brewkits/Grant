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
 * Regression test for Issue #53 — "the Android system permission dialog does not open".
 *
 * Background
 * ----------
 * Since v1.4.3 (commit 7f5257c) the main Android `request()` path was rewired to require a
 * [dev.brewkits.grant.GrantLauncher] registered via `setLauncher()`. When no launcher was
 * registered, `requestInternal()` logged an error and returned [GrantStatus.DENIED] **without
 * ever showing the system dialog**. iOS was unaffected (no launcher concept), which is exactly
 * the "only Android" symptom the reporter saw on 2.1.0 and 2.2.0.
 *
 * This contradicted the library's "No Lifecycle Binding" promise: the self-contained transparent
 * [GrantRequestActivity] — declared in grant-core's manifest — could already open the dialog from
 * any context. The fix restores that fallback: when no launcher is set, `request()` routes through
 * [GrantRequestActivity.requestGrants].
 *
 * What this test pins down
 * ------------------------
 * With **no** launcher registered, `delegate.request(CAMERA)` must invoke
 * [GrantRequestActivity.requestGrants] exactly once (i.e. it actually tries to open the dialog),
 * rather than short-circuiting to DENIED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], manifest = Config.NONE)
class Issue53NoLauncherFallbackTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)

        // The request path filters permissions through ManifestValidator.isPermissionDeclared,
        // so the permission must be declared in the (Robolectric) package manifest.
        val pm = shadowOf(context.packageManager)
        val packageInfo = android.content.pm.PackageInfo().apply {
            packageName = context.packageName
            requestedPermissions = arrayOf(Manifest.permission.CAMERA)
        }
        pm.installPackage(packageInfo)

        // CAMERA not yet granted → checkStatus is NOT_DETERMINED → request must show the dialog.
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)
    }

    @After
    fun tearDown() {
        unmockkObject(GrantRequestActivity.Companion)
    }

    @Test
    fun `request without a registered launcher falls back to GrantRequestActivity`() = runBlocking {
        // Note: setLauncher is intentionally NOT called — this reproduces Issue #53.
        val fallbackCallCount = AtomicInteger(0)

        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            fallbackCallCount.incrementAndGet()
            "issue53-request-id"
        }
        // Complete immediately so the suspending await() does not block the test.
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED)
            }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        val status = delegate.request(AppGrant.CAMERA)

        assertEquals(
            1,
            fallbackCallCount.get(),
            "With no launcher registered, request() must fall back to GrantRequestActivity " +
                "(open the dialog) instead of silently returning DENIED. Got ${fallbackCallCount.get()} calls."
        )
        // CAMERA stayed denied in the shadow, so the resolved status is DENIED — but crucially the
        // dialog was attempted, which is the whole point of Issue #53.
        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    fun `multi-request without a registered launcher falls back to GrantRequestActivity`() = runBlocking {
        val pm = shadowOf(context.packageManager)
        val packageInfo = android.content.pm.PackageInfo().apply {
            packageName = context.packageName
            requestedPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        }
        pm.installPackage(packageInfo)
        shadowOf(context as Application).denyPermissions(Manifest.permission.RECORD_AUDIO)

        val fallbackCallCount = AtomicInteger(0)

        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            fallbackCallCount.incrementAndGet()
            "issue53-multi-id"
        }
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED)
            }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        delegate.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE))

        assertEquals(
            1,
            fallbackCallCount.get(),
            "Batch request with no launcher must fall back to a single GrantRequestActivity launch."
        )
    }
}
