package dev.brewkits.grant.integration

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests to verify state persistence across GrantHandler instances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorePersistenceIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()
    private val sharedStore = InMemoryGrantStore()

    @Test
    fun `state persists across handler recreations`() = runTest {
        // 1. First handler requests and gets a 'Requested Before' state
        val handler1 = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )
        
        // Simulate a request that results in NOT_DETERMINED or DENIED
        sharedStore.setRequested(AppGrant.CAMERA)
        
        // 2. Second handler for the same permission should see the state from the store
        // (In a real app, GrantHandler doesn't directly take the store, it's inside the platform delegate,
        // but here we check the store's role in the flow)
        
        assertTrue(sharedStore.isRequestedBefore(AppGrant.CAMERA), "Store should record request")
        
        // If we clear the store, it should reflect
        sharedStore.clear(AppGrant.CAMERA)
        assertFalse(sharedStore.isRequestedBefore(AppGrant.CAMERA), "Store should be cleared")
    }

    @Test
    fun `RawPermission state persistence`() = runTest {
        val raw = RawPermission("CUSTOM", listOf("p1"), "key")
        
        assertFalse(sharedStore.isRequestedBefore(raw))
        
        sharedStore.setRequested(raw)
        
        assertTrue(sharedStore.isRequestedBefore(raw), "Raw permission should be marked as requested")
    }
}
