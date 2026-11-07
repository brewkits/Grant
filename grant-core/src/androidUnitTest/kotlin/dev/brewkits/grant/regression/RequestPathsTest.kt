package dev.brewkits.grant.regression

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.PlatformConfig
import dev.brewkits.grant.impl.GrantRequestActivity
import dev.brewkits.grant.impl.PlatformGrantDelegate
import io.mockk.coEvery
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Behaviour of the request paths added or reworked in the audit that no other test reached:
 * the two-step LOCATION_ALWAYS flow through the Activity (including a blocked second launch),
 * the batch path through a registered launcher, a request whose pending result vanished, and the
 * grant-aware `openSettings(grant)`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R], manifest = Config.NONE)
class RequestPathsTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformGrantDelegate
    private val app get() = shadowOf(context as Application)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
        PlatformConfig.activity = null
        GrantRequestActivity.forceReleaseGuardForTest()
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = context.packageName
                requestedPermissions = arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                )
            },
        )
        app.denyPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(GrantRequestActivity.Companion)
        GrantRequestActivity.forceReleaseGuardForTest()
        PlatformConfig.activity = null
    }

    /** Mocks the Activity bridge; [onLaunch] runs per launch (1-based) and may grant permissions. */
    private fun mockActivityBridge(launched: (String) -> Boolean = { true }, onLaunch: (Int, List<String>) -> Unit) {
        var launches = 0
        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            launches++
            onLaunch(launches, secondArg())
            "req$launches"
        }
        coEvery { GrantRequestActivity.awaitLaunched(any(), any()) } answers { launched(firstArg()) }
        every { GrantRequestActivity.getResultDeferred(any()) } answers {
            CompletableDeferred<GrantRequestActivity.GrantResult>().apply { complete(GrantRequestActivity.GrantResult.GRANTED) }
        }
        every { GrantRequestActivity.cleanup(any()) } returns Unit
    }

    @Test
    fun `LOCATION_ALWAYS asks foreground first then background through the Activity`() = runBlocking {
        val asked = mutableListOf<List<String>>()
        mockActivityBridge { n, perms ->
            asked += perms
            app.grantPermissions(*(if (n == 1) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                   else arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)))
        }

        val status = delegate.request(AppGrant.LOCATION_ALWAYS)

        assertEquals(
            listOf(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            ),
            asked,
        )
        assertEquals(GrantStatus.GRANTED, status)
    }

    @Test
    fun `a blocked background launch leaves LOCATION_ALWAYS at PARTIAL_GRANTED instead of hanging`() = runBlocking {
        mockActivityBridge(launched = { it == "req1" }) { n, _ ->
            if (n == 1) app.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val status = delegate.request(AppGrant.LOCATION_ALWAYS)

        assertEquals(GrantStatus.PARTIAL_GRANTED, status, "foreground was granted; the blocked second step must not erase it")
    }

    @Test
    fun `a request whose pending result disappeared returns without granting`() = runBlocking {
        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } returns "gone"
        coEvery { GrantRequestActivity.awaitLaunched(any(), any()) } returns true
        every { GrantRequestActivity.getResultDeferred(any()) } returns null
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        // The request was recorded before the dialog step, so the status reads DENIED (asked, no
        // Activity to confirm permanence) rather than NOT_DETERMINED. What matters here: it returns,
        // and never invents a grant.
        val status = delegate.request(AppGrant.CAMERA)
        assertEquals(false, status == GrantStatus.GRANTED, "got $status")
    }

    @Test
    fun `batch request goes through the registered launcher once for all permissions`() = runBlocking {
        val launches = mutableListOf<List<String>>()
        delegate.setLauncher(object : GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                launches += permissions
                app.grantPermissions(*permissions.toTypedArray())
                onResult(permissions.associateWith { true })
            }
        })

        val result = delegate.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE))

        assertEquals(1, launches.size, "one dialog for the whole batch")
        assertEquals(setOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), launches.single().toSet())
        assertEquals(GrantStatus.GRANTED, result[AppGrant.CAMERA])
        assertEquals(GrantStatus.GRANTED, result[AppGrant.MICROPHONE])
    }

    @Test
    fun `openSettings for a grant opens this app's details page`() {
        delegate.openSettings(AppGrant.CAMERA)

        val started = app.nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
    }
}
