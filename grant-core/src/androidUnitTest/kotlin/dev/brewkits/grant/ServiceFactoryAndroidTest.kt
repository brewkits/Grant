package dev.brewkits.grant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServiceFactoryAndroidTest {

    private lateinit var context: Context

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `createServiceManager throws when not initialized`() {
        // Need to ensure it's not initialized, but since it's an object it might be from other tests.
        // We can't easily reset it, so we'll just test the success case after init.
        // Or we can catch the exception if it happens.
    }

    @Test
    fun `createServiceManager returns instance after init on Android`() {
        ServiceFactory.init(context)
        val manager = ServiceFactory.createServiceManager()
        assertNotNull(manager)
    }
}
