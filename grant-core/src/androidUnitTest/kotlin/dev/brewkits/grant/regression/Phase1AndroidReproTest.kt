package dev.brewkits.grant.regression

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.PlatformConfig
import dev.brewkits.grant.impl.GrantRequestActivity
import dev.brewkits.grant.impl.PlatformGrantDelegate
import dev.brewkits.grant.util.ManifestValidator
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 1 reproduction tests (see the remediation plan).
 *
 * **These tests are EXPECTED TO FAIL on the current code.** Each one encodes the *correct*
 * behaviour for a defect found in review. A red test here proves the defect is real and gives
 * the Phase 2 fix a target; a test that unexpectedly passes means the review finding was a
 * false alarm and the item must be dropped from the plan.
 *
 * Every test name starts with its review item id (R2, R4, R6) so results map 1:1 to the plan.
 *
 * Not reproducible under Robolectric (need a real device — tracked separately):
 *  - R1 iOS location resuming early on the delegate's initial callback
 *  - R3 background-activity-launch block when the app is not in the foreground
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S], manifest = Config.NONE)
class Phase1AndroidReproTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
        PlatformConfig.activity = null
        GrantRequestActivity.forceReleaseGuardForTest()
        declare(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    @After
    fun tearDown() {
        unmockkObject(GrantRequestActivity.Companion)
        unmockkObject(ManifestValidator)
        PlatformConfig.activity = null
        GrantRequestActivity.forceReleaseGuardForTest()
    }

    private fun declare(vararg permissions: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = context.packageName
                requestedPermissions = arrayOf(*permissions)
            },
        )
    }

    // ── R4: NOTIFICATION below API 33 is a silent no-op ────────────────────────────────────

    /**
     * Below API 33 there is no POST_NOTIFICATIONS runtime permission, so `request()` cannot
     * show a dialog. When the user has switched notifications off, the only way forward is the
     * app's notification settings screen — and the result must become DENIED_ALWAYS-capable
     * rather than staying NOT_DETERMINED forever.
     */
    @Test
    fun `R4 request(NOTIFICATION) on API 31 with notifications off opens notification settings`() = runBlocking {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationsEnabled(false)

        delegate.request(AppGrant.NOTIFICATION)

        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(
            started,
            "request(NOTIFICATION) must not be a silent no-op when notifications are disabled",
        )
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
    }

    @Test
    fun `R4 status leaves NOT_DETERMINED after request(NOTIFICATION) on API 31 with notifications off`() = runBlocking {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(nm).setNotificationsEnabled(false)

        delegate.request(AppGrant.NOTIFICATION)
        // Outlive the 200 ms notification cache so this reads the real state.
        Thread.sleep(250)
        val status = delegate.checkStatus(AppGrant.NOTIFICATION)

        assertTrue(
            status != GrantStatus.NOT_DETERMINED,
            "After a request the status must reflect that the user was asked; got $status, " +
                "which sends GrantHandler into an endless request loop",
        )
    }

    // ── R6: batch path drifts from the single-request path ─────────────────────────────────

    /**
     * SCHEDULE_EXACT_ALARM is special app access: `requestPermissions()` can never grant it
     * (see ExactAlarmRequestTest). The single-request path routes it to Settings; the batch path
     * must at least not hand it to the runtime-permission dialog.
     */
    @Test
    fun `R6 batch request does not pass special-access permissions to the runtime dialog`() = runBlocking {
        declare(Manifest.permission.CAMERA, Manifest.permission.SCHEDULE_EXACT_ALARM)
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)

        val requested = mutableListOf<List<String>>()
        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            requested += secondArg<List<String>>()
            "r6-batch"
        }
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED)
            }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        delegate.request(listOf(AppGrant.CAMERA, AppGrant.SCHEDULE_EXACT_ALARM))

        val all = requested.flatten()
        assertFalse(
            Manifest.permission.SCHEDULE_EXACT_ALARM in all,
            "SCHEDULE_EXACT_ALARM has no runtime dialog; passing it to requestPermissions() is " +
                "the dead click the single path was fixed to avoid. Passed: $all",
        )
    }

    /**
     * The single path filters by manifest *before* recording the request. The batch path
     * records first, so a permission the app never declared is marked "asked" and then
     * escalates to DENIED_ALWAYS — sending the user to Settings for a toggle that cannot exist.
     *
     * (ManifestValidator is mocked because it short-circuits to `true` under Robolectric —
     * which is itself review item 16: test-only bypass compiled into production code.)
     */
    @Test
    fun `R6 batch request does not mark undeclared permissions as requested`() = runBlocking {
        mockkObject(ManifestValidator)
        every { ManifestValidator.isPermissionDeclared(any(), any()) } returns false

        delegate.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE))

        assertFalse(
            store.isRequestedBefore(AppGrant.CAMERA),
            "CAMERA is not declared in the manifest, so nothing was ever asked; recording it " +
                "as requested makes the next checkStatus() report a permanent denial",
        )
        assertFalse(store.isRequestedBefore(AppGrant.MICROPHONE))
    }

    // ── R2: the dialog's own verdict is discarded ──────────────────────────────────────────

    /**
     * GrantRequestActivity computes DENIED_PERMANENTLY from the live
     * `shouldShowRequestPermissionRationale()` — but the delegate throws that result away and
     * re-derives the status once the finishing Activity is no longer a usable foreground
     * Activity (PlatformConfig.activity filters out `isFinishing`). With no Activity to
     * consult, a second denial is downgraded to DENIED, so the rationale loop never ends.
     */
    @Test
    fun `R2 second denial reported by the dialog yields DENIED_ALWAYS even without a foreground activity`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)
        store.setRequested(AppGrant.CAMERA) // the user has been asked before

        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } returns "r2"
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply {
                complete(GrantRequestActivity.GrantResult.DENIED_PERMANENTLY)
            }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        val status = delegate.request(AppGrant.CAMERA)

        assertEquals(
            GrantStatus.DENIED_ALWAYS,
            status,
            "The dialog reported DENIED_PERMANENTLY; the delegate must honour it instead of " +
                "falling back to the stored status because no Activity was resumed yet",
        )
    }

    // ── R5: a pending dialog blocks every status read for the same permission ──────────────

    /**
     * `request()` holds the per-permission mutex for as long as the system dialog is open
     * (up to 5 minutes), and `checkStatus()` takes the same mutex. A UI refresh on resume, or
     * any other screen asking "is CAMERA granted?", therefore hangs until the user answers.
     * GrantStatus.BUSY exists precisely so a caller is told "in flight" instead of waiting.
     */
    @Test
    fun `R5 checkStatus returns promptly while a request for the same permission awaits its dialog`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)

        val dialogNeverAnswered = CompletableDeferred<GrantRequestActivity.GrantResult>()
        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } returns "r5"
        every { GrantRequestActivity.getResultDeferred(any()) } returns dialogNeverAnswered
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        val pending = launch(Dispatchers.Default) { delegate.request(AppGrant.CAMERA) }
        delay(300) // let request() reach the dialog await while holding the mutex

        val observed = withTimeoutOrNull(1_500) { delegate.checkStatus(AppGrant.CAMERA) }
        pending.cancel()

        assertNotNull(
            observed,
            "checkStatus() blocked for >1.5s behind an unanswered dialog; it must return " +
                "(BUSY or the last known status) instead of queueing on the request's mutex",
        )
        Unit
    }

    // ── R7: a still-live dialog is declared "stale" after 60 s ─────────────────────────────

    /**
     * The launch guard treats its owner as stale 60 s after launch, but the request itself is
     * allowed to wait 5 minutes for the user. A user who reads the dialog for a minute lets a
     * *different* permission's request steal the guard and open a second Activity over the
     * first — the exact failure the owner-tracking guard exists to prevent.
     */
    @Test
    fun `R7 a pending request is not stolen from its owner after 60 seconds`() {
        val app = context as Application
        val first = GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.CAMERA))
        assertNotNull(shadowOf(app).nextStartedActivity, "first request must launch its Activity")
        assertFalse(
            GrantRequestActivity.getResultDeferred(first)!!.isCompleted,
            "precondition: the first dialog is still on screen, unanswered",
        )

        // 61 s later — the user is still reading the dialog.
        val field = GrantRequestActivity::class.java.getDeclaredField("lastActivityLaunchTime")
        field.isAccessible = true
        field.setLong(null, System.currentTimeMillis() - 61_000)

        GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.RECORD_AUDIO))

        assertEquals(
            null,
            shadowOf(app).nextStartedActivity,
            "a second GrantRequestActivity was launched over a dialog that is still awaiting " +
                "the user; the guard must not be taken from a live, unanswered owner",
        )
    }
}
