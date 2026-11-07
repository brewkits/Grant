package dev.brewkits.grant.regression

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.impl.GrantRequestActivity
import dev.brewkits.grant.impl.PlatformGrantDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
 * LOCATION_ALWAYS needs two dialogs on Android 11+ (foreground, then background). The first used
 * the app's registered [GrantLauncher], but the second always went through GrantRequestActivity —
 * ignoring the launcher. An app that binds its own launcher (multi-process, custom host Activity)
 * would see the first step through its launcher and the second through a different mechanism
 * that its process may not even be able to complete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R], manifest = Config.NONE)
class LocationAlwaysLauncherTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = context.packageName
                requestedPermissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                )
            },
        )
        shadowOf(context as Application).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(GrantRequestActivity.Companion)
        GrantRequestActivity.forceReleaseGuardForTest()
    }

    @Test
    fun `second LOCATION_ALWAYS step uses the registered launcher too`() = runBlocking {
        val launches = mutableListOf<List<String>>()
        delegate.setLauncher(object : GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                launches += permissions
                // The user allows whatever is asked, like tapping "Allow" each time.
                shadowOf(context as Application).grantPermissions(*permissions.toTypedArray())
                onResult(permissions.associateWith { true })
            }
        })

        val activityFallbackCalls = AtomicInteger(0)
        mockkObject(GrantRequestActivity.Companion)
        every { GrantRequestActivity.requestGrants(any(), any()) } answers {
            activityFallbackCalls.incrementAndGet()
            "unexpected-fallback"
        }
        coEvery { GrantRequestActivity.awaitLaunched(any(), any()) } returns false
        every { GrantRequestActivity.cleanup(any()) } returns Unit

        val status = delegate.request(AppGrant.LOCATION_ALWAYS)

        assertEquals(
            0,
            activityFallbackCalls.get(),
            "step 2 must not bypass the registered launcher via GrantRequestActivity",
        )
        assertEquals(
            listOf(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            ),
            launches,
            "both steps must go through the launcher, foreground first",
        )
        assertEquals(GrantStatus.GRANTED, status)
    }
}
