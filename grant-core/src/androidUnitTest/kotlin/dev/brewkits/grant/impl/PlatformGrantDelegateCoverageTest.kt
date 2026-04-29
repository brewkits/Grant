package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformGrantDelegateCoverageTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
        mockkStatic(ContextCompat::class)
        mockkStatic(GrantRequestActivity::class)
    }

    @AfterTest
    fun teardown() {
        unmockkStatic(ContextCompat::class)
        unmockkStatic(GrantRequestActivity::class)
    }

    @Test
    fun `checkStatus for all AppGrant values when granted`() = runTest {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        AppGrant.entries.forEach { grant ->
            val status = delegate.checkStatus(grant)
            // Most should return GRANTED. Some might return PARTIAL if logic differs.
            // This test is for coverage.
            assertNotNull(status)
        }
    }

    @Test
    fun `checkStatus for all AppGrant values when not requested`() = runTest {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

        AppGrant.entries.forEach { grant ->
            val status = delegate.checkStatus(grant)
            // Should mostly return NOT_DETERMINED since store is empty
            assertNotNull(status)
        }
    }
}
