package dev.brewkits.grant.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.ServiceType
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformServiceDelegateAndroidTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformServiceDelegate

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        delegate = PlatformServiceDelegate(context)
    }

    @Test
    fun `checkServiceStatus for all ServiceType values`() = runTest {
        ServiceType.entries.forEach { type ->
            val status = delegate.checkServiceStatus(type)
            assertNotNull(status)
        }
    }
}
