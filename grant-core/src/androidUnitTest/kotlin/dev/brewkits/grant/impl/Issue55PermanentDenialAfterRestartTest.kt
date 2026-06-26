package dev.brewkits.grant.impl

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantFactory
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.PlatformConfig
import dev.brewkits.grant.SharedPreferencesGrantStore
import dev.brewkits.grant.setRequested
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
 * Regression test for the *permanent*-denial half of Issue #55.
 *
 * [Issue55RationaleAfterRestartTest] covers the soft-denial case, where the OS-persisted
 * `shouldShowRequestPermissionRationale()` flag is still `true` after restart and so a
 * fresh process can recover the DENIED status from the OS alone.
 *
 * A *permanently* denied permission is harder: the OS sets the flag back to `false` (it is
 * indistinguishable from "never asked" via that signal alone), so the only thing that tells
 * "permanently denied" apart from "fresh install" after the process is killed is Grant's own
 * record that it has asked before. With [InMemoryGrantStore] that record is lost on restart,
 * so `checkStatus()` returns NOT_DETERMINED and the request is silently swallowed — verified
 * on-device. [SharedPreferencesGrantStore] persists that record, so a fresh process correctly
 * reports DENIED_ALWAYS and the handler routes the user to Settings instead of no-op-ing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Issue55PermanentDenialAfterRestartTest {

    private lateinit var context: Context
    private lateinit var activity: Activity

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        PlatformConfig.activity = activity
        // Permanent denial: permission denied AND the OS rationale flag is false (its default).
        shadowOf(context as Application).denyPermissions(Manifest.permission.CAMERA)
    }

    @After
    fun tearDown() {
        PlatformConfig.activity = null
        SharedPreferencesGrantStore(context).clear()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `persistent store reports DENIED_ALWAYS after restart for a permanently denied permission`() = runBlocking {
        // Record the prior request, then drop that instance — the persisted file is all that
        // survives, exactly as it would after the process is killed and restarted.
        SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)

        val afterRestartStore = SharedPreferencesGrantStore(context)
        val delegate = PlatformGrantDelegate(context, afterRestartStore)

        val status = delegate.checkStatus(AppGrant.CAMERA)

        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `in-memory store loses the request history on restart and misreports NOT_DETERMINED`() = runBlocking {
        // Documents the original bug: a fresh in-memory store after restart has no record of
        // the prior request, so a permanent denial is indistinguishable from a fresh install.
        val afterRestartStore = InMemoryGrantStore()
        val delegate = PlatformGrantDelegate(context, afterRestartStore)

        val status = delegate.checkStatus(AppGrant.CAMERA)

        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `default factory selects the persistent store so DENIED_ALWAYS survives restart`() = runBlocking {
        // Records the request through the same persistent file the default factory uses, then
        // builds a brand-new GrantManager via GrantFactory.create(context) with NO explicit store.
        // If the default were in-memory this would return NOT_DETERMINED; DENIED_ALWAYS proves the
        // default path is wired to SharedPreferencesGrantStore.
        SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)

        val managerAfterRestart = GrantFactory.create(context)

        val status = managerAfterRestart.checkStatus(AppGrant.CAMERA)

        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }
}
