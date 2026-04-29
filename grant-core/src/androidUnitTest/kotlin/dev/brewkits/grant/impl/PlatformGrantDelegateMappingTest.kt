package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.InMemoryGrantStore
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformGrantDelegateMappingTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
    }

    @Test
    fun `test mapping for all AppGrant values`() = runTest {
        AppGrant.entries.forEach { grant ->
            delegate.checkStatus(grant)
        }
    }
    
    @Test
    @Config(sdk = [30]) // Android 11
    fun `test mapping for all AppGrant values on Android 11`() = runTest {
        AppGrant.entries.forEach { grant ->
            delegate.checkStatus(grant)
        }
    }

    @Test
    @Config(sdk = [28])
    fun `test location and storage on Android 9`() = runTest {
        delegate.checkStatus(AppGrant.LOCATION_ALWAYS)
        delegate.checkStatus(AppGrant.STORAGE)
    }

    @Test
    @Config(sdk = [29])
    fun `test location always on Android 10`() = runTest {
        delegate.checkStatus(AppGrant.LOCATION_ALWAYS)
    }

    @Test
    @Config(sdk = [33])
    fun `test storage on Android 13`() = runTest {
        delegate.checkStatus(AppGrant.STORAGE)
    }
}
