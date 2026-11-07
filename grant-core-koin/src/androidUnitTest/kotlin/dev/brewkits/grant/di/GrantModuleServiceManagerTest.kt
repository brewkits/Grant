package dev.brewkits.grant.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.GrantAndServiceChecker
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceManager
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The documented Koin setup is `androidContext(this)` + `grantModule` + `grantPlatformModule`.
 * `grantModule` registers `ServiceManager` through `ServiceFactory.createServiceManager()`, which
 * on Android throws unless `ServiceFactory.init(context)` was called separately — something the
 * Koin path never does and the docs do not mention. Resolving `ServiceManager` (or the
 * `GrantAndServiceChecker` built on it) therefore crashed at runtime for a correctly configured app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrantModuleServiceManagerTest : KoinTest {

    @BeforeTest
    fun forgetAnyEarlierInit() {
        // ServiceFactory is a process-wide object; make the test independent of test order.
        ServiceFactory::class.java.getDeclaredField("applicationContext").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun startDocumentedSetup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        startKoin {
            modules(module { single<Context> { context } }, grantPlatformModule, grantModule)
        }
    }

    @Test
    fun `ServiceManager resolves with only the documented Koin modules`() {
        startDocumentedSetup()
        assertNotNull(getKoin().get<ServiceManager>())
    }

    @Test
    fun `GrantAndServiceChecker resolves with only the documented Koin modules`() {
        startDocumentedSetup()
        assertNotNull(getKoin().get<GrantManager>())
        assertNotNull(getKoin().get<GrantAndServiceChecker>())
    }
}
