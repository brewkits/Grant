package dev.brewkits.grant.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformOpenSettingsTest {

    private lateinit var context: Context
    private lateinit var grantDelegate: PlatformGrantDelegate
    private lateinit var serviceDelegate: PlatformServiceDelegate

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        grantDelegate = PlatformGrantDelegate(context, InMemoryGrantStore())
        serviceDelegate = PlatformServiceDelegate(context)
    }

    @Test
    fun `test openSettings`() {
        grantDelegate.openSettings()
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(application)
        val nextIntent = shadowApp.nextStartedActivity
        kotlin.test.assertNotNull(nextIntent, "Settings intent should be fired")
        kotlin.test.assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, nextIntent.action)
    }

    @Test
    fun `test openServiceSettings`() = runTest {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowApp = org.robolectric.Shadows.shadowOf(application)
        
        ServiceType.entries.forEach { type ->
            serviceDelegate.openServiceSettings(type)
            val nextIntent = shadowApp.nextStartedActivity
            kotlin.test.assertNotNull(nextIntent, "Service settings intent should be fired for $type")
            kotlin.test.assertTrue(nextIntent.action != null, "Intent action should not be null for $type")
        }
    }
}
