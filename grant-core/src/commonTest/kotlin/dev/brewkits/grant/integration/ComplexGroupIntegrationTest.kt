package dev.brewkits.grant.integration

import app.cash.turbine.test
import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for complex GrantGroupHandler scenarios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComplexGroupIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    @BeforeTest
    fun setup() {
        mockGrantManager.reset()
    }

    @Test
    fun `group request - partially granted scenario`() = runTest {
        // Setup: Camera will be granted, Microphone denied
        mockGrantManager.configure(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        mockGrantManager.configure(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED, GrantStatus.DENIED)

        val groupHandler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        var allGrantedCalled = false
        
        // Request both
        groupHandler.request { allGrantedCalled = true }
        testScope.advanceUntilIdle()

        // Verify: Not all granted
        assertFalse(allGrantedCalled, "onAllGranted should NOT be called if one is denied")
        
        // Verify: UI State shows rationale for the denied one (Microphone)
        groupHandler.state.test {
            val state = awaitItem()
            assertEquals(AppGrant.MICROPHONE, state.currentGrant)
            assertTrue(state.showRationale)
        }
    }

    @Test
    fun `group request - handles already granted permissions by skipping them`() = runTest {
        // Setup: Camera already granted, Mic needs request
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.configure(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        var allGrantedCalled = false
        groupHandler.request { allGrantedCalled = true }
        testScope.advanceUntilIdle()

        // Verify: Camera wasn't requested again, Mic was
        assertTrue(allGrantedCalled, "Should complete because Mic was granted and Camera was already granted")
        assertEquals(1, mockGrantManager.requestedGrants.filter { it == AppGrant.MICROPHONE }.size)
        assertEquals(0, mockGrantManager.requestedGrants.filter { it == AppGrant.CAMERA }.size)
    }

    @Test
    fun `group request - stops at first DENIED_ALWAYS to show settings`() = runTest {
        mockGrantManager.configure(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED, GrantStatus.DENIED_ALWAYS)
        mockGrantManager.configure(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        groupHandler.request { }
        testScope.advanceUntilIdle()

        groupHandler.state.test {
            val state = awaitItem()
            assertEquals(AppGrant.CAMERA, state.currentGrant)
            assertTrue(state.showSettingsGuide, "Should stop at Camera and show Settings guide")
        }
    }
}
