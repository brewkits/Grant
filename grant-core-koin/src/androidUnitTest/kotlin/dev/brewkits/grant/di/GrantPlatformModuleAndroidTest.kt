package dev.brewkits.grant.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.impl.PlatformGrantDelegate
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Exercises `grantPlatformModule`'s `single { }` with a real Android [Context], which is what
 * closes the 0% coverage gap on `GrantPlatformModule_androidKt` recorded in this module's
 * `build.gradle.kts` — the definition was previously never resolved by any test, since
 * [GrantDiTest] only ever loads `grantModule` on its own and asserts that resolution *fails*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrantPlatformModuleAndroidTest : KoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `grantPlatformModule resolves a PlatformGrantDelegate given a real Context`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        startKoin {
            modules(
                module { single<Context> { context } },
                grantPlatformModule,
            )
        }

        val delegate = getKoin().get<PlatformGrantDelegate>()
        assertNotNull(delegate)
    }

    @Test
    fun `grantPlatformModule returns the same singleton instance on repeated resolution`() {
        val context: Context = ApplicationProvider.getApplicationContext()

        startKoin {
            modules(
                module { single<Context> { context } },
                grantPlatformModule,
            )
        }

        val first = getKoin().get<PlatformGrantDelegate>()
        val second = getKoin().get<PlatformGrantDelegate>()
        assertSame(first, second, "grantPlatformModule's single { } must not create a new delegate per resolution")
    }
}
