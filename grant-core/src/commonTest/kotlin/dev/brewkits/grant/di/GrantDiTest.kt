package dev.brewkits.grant.di

import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.ServiceManager
import dev.brewkits.grant.GrantAndServiceChecker
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.*

class GrantDiTest : KoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `test grantModule injection`() {
        startKoin {
            modules(grantModule)
        }
        
        // Note: grantModule requires some platform bindings usually,
        // but it defines ServiceManager and GrantAndServiceChecker.
        // We might need to mock some platform parts if it fails.
    }
}
