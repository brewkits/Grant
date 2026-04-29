package dev.brewkits.grant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.impl.PlatformGrantDelegate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrantFactoryAndroidTest {

    private lateinit var context: Context

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `create should return a GrantManager on Android`() {
        val manager = GrantFactory.create(context)
        assertNotNull(manager)
    }

    @Test
    fun `create with store should return a GrantManager`() {
        val store = InMemoryGrantStore()
        val manager = GrantFactory.create(context, store)
        assertNotNull(manager)
    }
}
