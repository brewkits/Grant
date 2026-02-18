package dev.brewkits.grant.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for GrantHandler with GrantManager.
 *
 * Tests the complete flow of GrantHandler using GrantManager,
 * including state management, dialog flows, and user interactions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantHandlerIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    @Test
    fun `complete flow - NOT_DETERMINED to GRANTED`() = runTest {
        // Setup: Permission never requested
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        var onGrantedCalled = false

        // User clicks request button
        handler.request { onGrantedCalled = true }
        testScope.advanceUntilIdle()

        // Verify: Permission granted, callback invoked
        assertTrue(onGrantedCalled, "onGranted callback should be invoked")
        assertTrue(mockGrantManager.requestCalled, "Request should be called")
    }

    @Test
    fun `complete flow - NOT_DETERMINED to DENIED to GRANTED`() = runTest {
        // Setup: First request will be denied
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // First request - user denies (no rationale shown yet - prevents double dialog)
        handler.request(
            rationaleMessage = "Need camera for photos",
            settingsMessage = "Enable camera in Settings"
        ) { }
        testScope.advanceUntilIdle()

        // Verify: NO rationale shown after first denial (by design)
        handler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale, "Rationale should NOT be shown after first denial")
        }

        // Update mock status to DENIED (simulates what happens in real app)
        mockGrantManager.mockStatus = GrantStatus.DENIED

        // User tries again - NOW rationale is shown
        handler.request(
            rationaleMessage = "Need camera for photos",
            settingsMessage = "Enable camera in Settings"
        ) { }
        testScope.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale, "Rationale should be shown on second request")
        }

        // User confirms rationale, request again, and grants
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Verify: Permission granted
        handler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale, "Rationale should be hidden")
        }
    }

    @Test
    fun `complete flow - DENIED to Settings guide`() = runTest {
        // Setup: Permission already denied
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // First request - show rationale (not first system dialog, so rationale shows)
        handler.request(
            rationaleMessage = "Need camera",
            settingsMessage = "Enable in Settings"
        ) { }
        testScope.advanceUntilIdle()

        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale, "Rationale should be shown")
        }

        // User denies again - becomes permanent
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Verify: Settings guide shown after permanent denial
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Settings guide should be shown")
        }
    }

    @Test
    fun `Settings guide flow - user confirms and enables`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // Request shows settings guide
        handler.request(settingsMessage = "Enable in Settings") { }
        testScope.advanceUntilIdle()

        // User clicks "Open Settings"
        handler.onSettingsConfirmed()
        testScope.advanceUntilIdle()

        // Verify: openSettings called
        assertTrue(mockGrantManager.openSettingsCalled, "openSettings should be called")

        // Simulate user enabling in Settings and returning
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        // Verify: Status updated to GRANTED
        handler.status.test {
            val status = awaitItem()
            assertEquals(GrantStatus.GRANTED, status)
        }
    }

    @Test
    fun `Dialog dismissal clears state`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // First request - rationale shown (status already DENIED, so not first system dialog)
        handler.request(rationaleMessage = "Need camera") { }
        testScope.advanceUntilIdle()

        handler.state.test {
            assertTrue(awaitItem().showRationale)
        }

        // User dismisses dialog
        handler.onDismiss()
        testScope.advanceUntilIdle()

        // Verify: Dialog hidden
        handler.state.test {
            val state = awaitItem()
            assertFalse(state.showRationale)
            assertFalse(state.showSettingsGuide)
        }
    }

    @Test
    fun `Multiple sequential requests work correctly`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // First request - denied (no rationale yet)
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        handler.request { }
        testScope.advanceUntilIdle()

        // Update status to DENIED (simulates real app behavior)
        mockGrantManager.mockStatus = GrantStatus.DENIED

        // Second request - rationale shown
        handler.request { }
        testScope.advanceUntilIdle()

        handler.state.test {
            assertTrue(awaitItem().showRationale, "Rationale should be shown on second request")
        }

        // User confirms rationale, denied again permanently
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Third request - granted (after user enables in Settings)
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        var granted = false
        handler.request { granted = true }
        testScope.advanceUntilIdle()

        assertTrue(granted, "Final request should grant permission")
    }

    @Test
    fun `Status refresh updates state correctly`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // Initial status
        handler.status.test {
            assertEquals(GrantStatus.NOT_DETERMINED, awaitItem())
        }

        // User grants in Settings (external to app)
        mockGrantManager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        // Verify: Status updated
        handler.status.test {
            assertEquals(GrantStatus.GRANTED, awaitItem())
        }
    }

    @Test
    fun `Custom messages are used in state`() = runTest {
        val customRationale = "Custom rationale message"
        val customSettings = "Custom settings message"

        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        handler.request(
            rationaleMessage = customRationale,
            settingsMessage = customSettings
        ) { }
        testScope.advanceUntilIdle()

        // Verify: Custom messages used
        handler.state.test {
            val state = awaitItem()
            assertEquals(customRationale, state.rationaleMessage)
        }
    }
}
