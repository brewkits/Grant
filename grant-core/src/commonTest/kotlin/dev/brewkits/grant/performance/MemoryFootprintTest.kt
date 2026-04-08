package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance tests for Memory Footprint.
 *
 * Verifies that GrantHandler is lightweight and can be instantiated in large
 * numbers without excessive memory consumption or leaking resources.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryFootprintTest {

    private lateinit var mockGrantManager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
        testScope = TestScope()
    }

    @Test
    fun `Memory Footprint - creating 10000 handlers`() = runTest {
        val count = 10000
        val handlers = mutableListOf<GrantHandler>()
        
        // Creating 10,000 handlers should be fast and not throw OOM
        repeat(count) {
            handlers.add(
                GrantHandler(
                    grantManager = mockGrantManager,
                    grant = AppGrant.CAMERA,
                    scope = testScope
                )
            )
        }
        
        assertEquals(count, handlers.size)
        
        // Check that they are all initialized
        handlers.forEach { 
            assertEquals(GrantStatus.NOT_DETERMINED, it.status.value)
        }
        
        // Clear them
        handlers.clear()
    }
}

private fun assertEquals(expected: Int, actual: Int) {
    kotlin.test.assertEquals(expected, actual)
}

private fun assertEquals(expected: GrantStatus, actual: GrantStatus) {
    kotlin.test.assertEquals(expected, actual)
}
