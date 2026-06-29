package dev.brewkits.grant.impl

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.PlatformConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Regression test for the Issue #55 follow-up reported on the issue thread:
 * after a *second* denial the settings guide never appeared — the rationale dialog
 * was shown again instead — and the flow only behaved correctly after an app restart.
 *
 * Root cause
 * ----------
 * The general `AppGrant` branch of [PlatformGrantDelegate.checkStatus] short-circuited
 * on the in-memory `store.getStatus()` value *before* consulting the OS-persisted
 * `shouldShowRequestPermissionRationale()` flag:
 *
 * ```
 * val storedStatus = store.getStatus(appGrant)
 * if (storedStatus == DENIED || storedStatus == DENIED_ALWAYS) storedStatus
 * else { /* rationale check */ }
 * ```
 *
 * The first denial cached `DENIED` in [InMemoryGrantStore]. When the user then denied a
 * second time — at which point Android flips `shouldShowRequestPermissionRationale()` to
 * `false` (permanent denial) — `checkStatus()` returned the stale cached `DENIED` and
 * never observed the permanent denial, so [dev.brewkits.grant.GrantHandler] kept treating
 * it as a soft denial and re-showed the rationale.
 *
 * Because `store.getStatus()` is in-memory only (status is session state — see
 * [dev.brewkits.grant.SharedPreferencesGrantStore]), a process restart cleared the stale
 * `DENIED` and the OS rationale flag was consulted again — which is exactly why the
 * reporter saw the correct behavior *only after restart*.
 *
 * The fix consults the OS rationale flag first, so the in-session
 * `DENIED → DENIED_ALWAYS` transition is detected without a restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Issue55InSessionDeniedAlwaysTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate
    private lateinit var activity: Activity

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        PlatformConfig.activity = activity
    }

    @After
    fun tearDown() {
        PlatformConfig.activity = null
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `second denial transitions DENIED to DENIED_ALWAYS in the same session`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)

        // Simulate the state right after a SECOND denial within the same process:
        // - the first denial recorded the request and cached DENIED in the store,
        // - the second denial flipped the OS rationale flag off (permanent denial).
        // The stale cached DENIED must NOT mask this; checkStatus must report DENIED_ALWAYS
        // without requiring a process restart. (The old short-circuit returned the cached
        // DENIED here, which is the bug.)
        store.setRequested(AppGrant.CAMERA)
        store.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, false)

        assertEquals(GrantStatus.DENIED_ALWAYS, delegate.checkStatus(AppGrant.CAMERA))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `soft denial still reports DENIED while OS allows a rationale even with cached DENIED`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        store.setRequested(AppGrant.CAMERA)
        store.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)

        assertEquals(GrantStatus.DENIED, delegate.checkStatus(AppGrant.CAMERA))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `no Activity falls back to cached status so DENIED_ALWAYS is not downgraded`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)
        store.setRequested(AppGrant.CAMERA)
        store.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)

        // Without an Activity the OS rationale flag cannot be consulted; the last known
        // stored status is the best available signal.
        PlatformConfig.activity = null

        assertEquals(GrantStatus.DENIED_ALWAYS, delegate.checkStatus(AppGrant.CAMERA))
    }
}
