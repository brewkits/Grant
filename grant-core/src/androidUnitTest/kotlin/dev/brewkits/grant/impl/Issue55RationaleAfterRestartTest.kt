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
import dev.brewkits.grant.RawPermission
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
 * Regression test for Issue #55 — "Permission request is swallowed on app restart on Android".
 *
 * Background
 * ----------
 * `checkStatus()` disambiguated NOT_DETERMINED from a soft DENIED purely from
 * [InMemoryGrantStore.isRequestedBefore], an in-memory flag that is lost whenever the process
 * is killed and restarted. `Activity.shouldShowRequestPermissionRationale()` is tracked by the
 * OS itself and survives process death, but the old code only consulted it *after* the
 * in-memory flag already confirmed a prior request — so right after a restart it never got a
 * chance to run, and a real soft denial was misreported as NOT_DETERMINED.
 *
 * That false NOT_DETERMINED made [dev.brewkits.grant.GrantHandler] re-trigger the OS dialog
 * (which Android auto-resolves to DENIED almost instantly) and, because `hasShownRationaleDialog`
 * is also reset on restart, treat it as a brand-new first denial — silently finishing the flow
 * without ever showing the rationale dialog. The fix checks the OS-persisted rationale flag
 * before falling back to the in-memory store.
 *
 * What these tests pin down
 * -------------------------
 * `checkStatus()` must report DENIED — not NOT_DETERMINED — whenever the OS still says
 * `shouldShowRequestPermissionRationale() == true`, even with a brand-new [InMemoryGrantStore]
 * that has never recorded a request (i.e. simulating the state right after a process restart).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Issue55RationaleAfterRestartTest {

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
    fun `checkStatus reports DENIED after restart when OS still remembers a soft denial for AppGrant`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)

        // store is brand new — never called setRequested(), simulating a fresh process after restart.
        val status = delegate.checkStatus(AppGrant.CAMERA)

        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `checkStatus reports DENIED after restart when OS still remembers a soft denial for RawPermission`() = runBlocking {
        val permission = "android.permission.CUSTOM"
        shadowOf(context as Application).denyPermissions(permission)
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(permission, true)

        val raw = RawPermission("CUSTOM", listOf(permission), null)
        val status = delegate.checkStatus(raw)

        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `checkStatus reports DENIED after restart when OS still remembers a soft denial for LOCATION_ALWAYS`() = runBlocking {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            true
        )

        val status = delegate.checkStatus(AppGrant.LOCATION_ALWAYS)

        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `checkStatus still reports NOT_DETERMINED on a genuinely fresh install`() = runBlocking {
        // No prior OS interaction at all: shouldShowRequestPermissionRationale defaults to false.
        val status = delegate.checkStatus(AppGrant.CAMERA)

        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }
}
