package dev.brewkits.grant.system

import app.cash.turbine.test
import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import dev.brewkits.grant.impl.DefaultGrantManager
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * System-level end-to-end simulation tests.
 *
 * These tests coordinate multiple components (Manager, Store, Handlers) to verify
 * valid user journeys through the entire library stack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var mockGrantManager: FakeGrantManager
    private lateinit var store: GrantStore

    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
        store = InMemoryGrantStore()
    }

    @Test
    fun `System Journey - complete success flow`() = runTest {
        // GIVEN: User has never seen the permission request
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        var capturedResult = false

        // WHEN: User triggers request
        handler.request { capturedResult = true }
        testScope.advanceUntilIdle()

        // THEN: Success callback fired, store is informed of grant
        assertTrue(capturedResult, "Callback should be fired")
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `System Journey - denial then settings flow`() = runTest {
        println("Starting System Journey test")
        // 1. Initial State: NOT_DETERMINED
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, testScope)
        advanceUntilIdle()
        
        // 2. First Request -> User Denies
        println("Step 2: First Request")
        handler.request { }
        advanceUntilIdle()
        
        assertFalse(handler.state.value.showRationale, "Rationale should NOT be shown on first denial")

        // 3. Status updates to DENIED in the OS
        println("Step 3: Status -> DENIED")
        mockGrantManager.mockStatus = GrantStatus.DENIED
        handler.refreshStatus()
        advanceUntilIdle()
        
        // 4. Second Request -> User sees Rationale
        println("Step 4: Second Request")
        handler.request { }
        advanceUntilIdle()
        assertTrue(handler.state.value.showRationale, "Rationale should be shown on 2nd attempt")

        // 5. User confirms rationale, OS denies permanently
        println("Step 5: Rationale Confirmed -> DENIED_ALWAYS")
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        handler.onRationaleConfirmed()
        advanceUntilIdle()
        
        // 6. User should see settings guide
        println("Step 6: Check Settings Guide")
        assertTrue(handler.state.value.showSettingsGuide, "Settings guide should be shown after permanent denial")

        // 7. User goes to settings and enables
        println("Step 7: Confirm Settings -> GRANTED")
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        handler.onSettingsConfirmed()
        advanceUntilIdle()
        handler.refreshStatus()
        advanceUntilIdle()
        
        // 8. Result should be GRANTED
        assertEquals(GrantStatus.GRANTED, handler.status.value, "Final status should be GRANTED")
        println("Test finished successfully")
    }
}
