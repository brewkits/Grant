package dev.brewkits.grant.impl

import android.Manifest
import android.app.Application
import android.content.Context
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for PlatformGrantDelegate on Android.
 *
 * Uses Robolectric to simulate Android system states and verify
 * that the delegate correctly identifies granted/denied permissions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidDelegateIntegrationTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformGrantDelegate
    private lateinit var shadowApp: org.robolectric.shadows.ShadowApplication

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
        shadowApp = shadowOf(context as Application)
    }

    @Test
    fun `checkStatus returns GRANTED when permission is already granted`() = runTest {
        shadowApp.grantPermissions(Manifest.permission.CAMERA)
        
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, status)
    }

    @Test
    fun `checkStatus returns NOT_DETERMINED when permission is not granted`() = runTest {
        shadowApp.denyPermissions(Manifest.permission.CAMERA)
        
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    @Test
    fun `openSettings intent is correctly configured`() {
        delegate.openSettings()
        
        val nextStartedActivity = shadowApp.nextStartedActivity
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", nextStartedActivity.action)
        // Robolectric uses "org.robolectric.default" as default package name
        assertTrue(nextStartedActivity.data.toString().contains("package:"))
    }

    @Test
    fun `checkStatus for LOCATION returns GRANTED if both permissions are granted`() = runTest {
        shadowApp.grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val status = delegate.checkStatus(AppGrant.LOCATION)
        assertEquals(GrantStatus.GRANTED, status)
    }
}
