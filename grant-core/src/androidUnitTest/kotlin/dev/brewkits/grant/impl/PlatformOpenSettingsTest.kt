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
    }

    @Test
    fun `test openServiceSettings`() = runTest {
        ServiceType.entries.forEach { type ->
            serviceDelegate.openServiceSettings(type)
        }
    }
}
