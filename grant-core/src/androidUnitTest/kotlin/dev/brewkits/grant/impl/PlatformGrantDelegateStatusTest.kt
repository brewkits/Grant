package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.RawPermission
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class PlatformGrantDelegateStatusTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
    }

    @Test
    fun `checkStatus returns NOT_DETERMINED for default CAMERA`() = runTest {
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    @Test
    fun `checkStatus returns DENIED when requested before and denied in OS`() = runTest {
        store.setRequested(AppGrant.CAMERA)
        // OS still says denied (no shadow permissions added)
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    fun `checkStatus returns DENIED_ALWAYS when store says DENIED_ALWAYS`() = runTest {
        store.setRequested(AppGrant.CAMERA)
        store.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }

    @Test
    fun `checkStatus returns GRANTED when permission is granted in OS`() = runTest {
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.CAMERA)

        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, status)
    }

    @Test
    fun `checkStatus handles RawPermission correctly`() = runTest {
        val raw = RawPermission("CUSTOM", listOf("android.permission.CUSTOM"), null)
        val status1 = delegate.checkStatus(raw)
        assertEquals(GrantStatus.NOT_DETERMINED, status1)

        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        shadowApp.grantPermissions("android.permission.CUSTOM")
        val status2 = delegate.checkStatus(raw)
        assertEquals(GrantStatus.GRANTED, status2)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `checkStatus for LOCATION_ALWAYS handles background vs foreground`() = runTest {
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        
        // No permissions
        assertEquals(GrantStatus.NOT_DETERMINED, delegate.checkStatus(AppGrant.LOCATION_ALWAYS))
        
        // Just foreground
        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowApp.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(GrantStatus.PARTIAL_GRANTED, delegate.checkStatus(AppGrant.LOCATION_ALWAYS))
        
        // Foreground and background
        shadowApp.grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.LOCATION_ALWAYS))
    }
}
