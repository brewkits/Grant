package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrantRequestActivityRoboTest {

    private lateinit var context: Context

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `testGenerateUniqueRequestIds`() {
        val requestIds = mutableSetOf<String>()
        repeat(10) {
            val requestId = GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.VIBRATE))
            requestIds.add(requestId)
            GrantRequestActivity.cleanup(requestId)
        }
        assertEquals(10, requestIds.size)
    }

    @Test
    fun `testCleanupRemovesFlows`() {
        val requestId = GrantRequestActivity.requestGrants(context, listOf(Manifest.permission.VIBRATE))
        assertNotNull(GrantRequestActivity.getResultDeferred(requestId))
        GrantRequestActivity.cleanup(requestId)
        assertEquals(null, GrantRequestActivity.getResultDeferred(requestId))
    }
}
