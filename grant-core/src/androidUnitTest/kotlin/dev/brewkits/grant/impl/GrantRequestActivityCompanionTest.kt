package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrantRequestActivityCompanionTest {

    private lateinit var context: Context

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test requestGrants creates deferred and returns id`() {
        val requestId = GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.CAMERA))
        
        assertNotNull(requestId)
        val deferred = GrantRequestActivity.getResultDeferred(requestId)
        assertNotNull(deferred)
        
        // Clean up
        GrantRequestActivity.cleanup(requestId)
        assertNull(GrantRequestActivity.getResultDeferred(requestId))
    }

    @Test
    fun `test multiple requestGrants generate unique ids`() {
        val id1 = GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.CAMERA))
        val id2 = GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.RECORD_AUDIO))
        
        assertNotEquals(id1, id2)
        
        GrantRequestActivity.cleanup(id1)
        GrantRequestActivity.cleanup(id2)
    }

    @Test
    fun `test empty grants`() {
        val id = GrantRequestActivity.requestGrants(context, emptyList())
        assertNotNull(id)
        GrantRequestActivity.cleanup(id)
    }
}
