package dev.brewkits.grant.impl

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class PlatformGrantDelegateLocationAlwaysTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
    }

    @Test
    fun `test LOCATION_ALWAYS checkStatus returns DENIED when fine location not granted`() = runBlocking {
        val app = shadowOf(context as Application)
        app.denyPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        
        store.setRequested(AppGrant.LOCATION_ALWAYS)

        val status = delegate.checkStatus(AppGrant.LOCATION_ALWAYS)
        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    fun `test LOCATION_ALWAYS checkStatus returns GRANTED when both granted`() = runBlocking {
        val app = shadowOf(context as Application)
        app.grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)

        val status = delegate.checkStatus(AppGrant.LOCATION_ALWAYS)
        assertEquals(GrantStatus.GRANTED, status)
    }
}
