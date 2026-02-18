package dev.brewkits.grant.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.MultiGrantFakeManager
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
 * Integration tests for GrantGroupHandler with GrantManager.
 *
 * Tests complete multi-permission flows for real-world use cases:
 * - Video recording (Camera + Microphone)
 * - Location photos (Camera + Location)
 * - Contact sync (Contacts + Storage)
 *
 * Focuses on sequential request handling, complex state management,
 * and user interaction flows across multiple permissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantGroupHandlerIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = MultiGrantFakeManager()

    // ==================== Video Recording Flow (Camera + Microphone) ====================

    @Test
    fun `Video recording flow Both permissions granted on first request`() = runTest {
        // Setup: Neither permission requested before
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        var videoRecordingStarted = false

        // User clicks "Start Video Recording"
        handler.request(
            rationaleMessages = mapOf(
                AppGrant.CAMERA to "Camera needed for recording video",
                AppGrant.MICROPHONE to "Microphone needed for recording audio"
            )
        ) {
            videoRecordingStarted = true
        }
        testScope.advanceUntilIdle()

        // Verify: Both granted, video recording started
        assertTrue(videoRecordingStarted, "Video recording should start")
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CAMERA))
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE))
    }

    @Test
    fun `Video recording flow Camera granted Microphone denied`() = runTest {
        // Setup: First request
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        var videoRecordingStarted = false

        handler.request(
            rationaleMessages = mapOf(
                AppGrant.CAMERA to "Camera for video",
                AppGrant.MICROPHONE to "Microphone for audio"
            )
        ) {
            videoRecordingStarted = true
        }
        testScope.advanceUntilIdle()

        // Verify: Recording NOT started (missing Microphone)
        assertFalse(videoRecordingStarted, "Video recording should NOT start")

        // Verify: Rationale shown for Microphone
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale, "Should show rationale for Microphone")
            assertEquals(AppGrant.MICROPHONE, state.currentGrant)
            assertEquals("Microphone for audio", state.rationaleMessage)
        }
    }

    @Test
    fun `Video recording flow Camera denied permanently stop immediately`() = runTest {
        // Setup: Camera already denied permanently
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        handler.request(
            settingsMessages = mapOf(
                AppGrant.CAMERA to "Enable Camera in Settings > Privacy > Camera"
            )
        ) { }
        testScope.advanceUntilIdle()

        // Verify: Settings guide shown for Camera, Microphone never requested
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Should show settings guide")
            assertEquals(AppGrant.CAMERA, state.currentGrant)
        }

        assertFalse(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE),
            "Microphone should NOT be requested")
    }

    // ==================== Location Photos Flow (Camera + Location) ====================

    @Test
    fun `Location photos flow - Both already granted`() = runTest {
        // Setup: User previously granted both
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.LOCATION),
            scope = testScope
        )

        var locationPhotosEnabled = false

        handler.request { locationPhotosEnabled = true }
        testScope.advanceUntilIdle()

        // Verify: No dialogs shown, feature enabled immediately
        assertTrue(locationPhotosEnabled, "Location photos should be enabled")

        handler.state.test {
            val state = awaitItem()
            assertFalse(state.isVisible, "No dialog should be shown")
            assertEquals(2, state.grantedGrants.size)
        }
    }

    @Test
    fun `Location photos flow Sequential granting Camera then Location`() = runTest {
        // Setup
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.LOCATION, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.LOCATION),
            scope = testScope
        )

        var locationPhotosEnabled = false

        // Track granted permissions progress
        handler.state.test {
            skipItems(1) // Skip initial state

            handler.request { locationPhotosEnabled = true }
            testScope.advanceUntilIdle()

            // First: Camera granted
            val state1 = awaitItem()
            assertTrue(state1.grantedGrants.contains(AppGrant.CAMERA))
            assertEquals(1, state1.grantedGrants.size)

            // Second: Location granted
            val state2 = awaitItem()
            assertTrue(state2.grantedGrants.contains(AppGrant.CAMERA))
            assertTrue(state2.grantedGrants.contains(AppGrant.LOCATION))
            assertEquals(2, state2.grantedGrants.size)
        }

        assertTrue(locationPhotosEnabled, "Location photos should be enabled")
    }

    // ==================== Complex Multi-Step Flow ====================

    @Test
    fun `Complex flow Denial rationale re-request then grant`() = runTest {
        // Setup: Both not determined initially
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        // First attempt: Camera granted, Microphone denied
        handler.request(
            rationaleMessages = mapOf(
                AppGrant.MICROPHONE to "We need microphone for audio"
            )
        ) { }
        testScope.advanceUntilIdle()

        // Verify: Rationale shown
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showRationale)
            assertEquals(AppGrant.MICROPHONE, state.currentGrant)
        }

        // User reads rationale and confirms - now grants permission
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Verify: Microphone now granted
        handler.statuses.test {
            val statuses = awaitItem()
            assertEquals(GrantStatus.GRANTED, statuses[AppGrant.MICROPHONE])
        }
    }

    @Test
    fun `Complex flow Rationale rejected twice becomes permanent denial`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA),
            scope = testScope
        )

        // First denial - show rationale
        handler.request(
            rationaleMessages = mapOf(AppGrant.CAMERA to "Camera needed"),
            settingsMessages = mapOf(AppGrant.CAMERA to "Enable in Settings")
        ) { }
        testScope.advanceUntilIdle()

        // User confirms rationale, denies again (becomes permanent)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Verify: Settings guide now shown
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Should show settings guide")
            assertEquals("Enable in Settings", state.settingsMessage)
        }
    }

    // ==================== Settings Navigation Flow ====================

    @Test
    fun `Settings flow - User enables all permissions in Settings`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        // Request shows settings guide for Camera
        handler.request(
            settingsMessages = mapOf(
                AppGrant.CAMERA to "Enable Camera in Settings"
            )
        ) { }
        testScope.advanceUntilIdle()

        // User clicks "Open Settings"
        handler.onSettingsConfirmed()
        testScope.advanceUntilIdle()

        assertTrue(mockGrantManager.openSettingsCalled, "Should open Settings")

        // Simulate user enabling both permissions and returning to app
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)
        handler.refreshAllStatuses()
        testScope.advanceUntilIdle()

        // Verify: Both permissions now granted
        handler.statuses.test {
            val statuses = awaitItem()
            assertEquals(GrantStatus.GRANTED, statuses[AppGrant.CAMERA])
            assertEquals(GrantStatus.GRANTED, statuses[AppGrant.MICROPHONE])
        }

        handler.state.test {
            val state = awaitItem()
            assertEquals(2, state.grantedGrants.size)
        }
    }

    @Test
    fun `Settings flow - User enables only one permission`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        handler.request { }
        testScope.advanceUntilIdle()

        handler.onSettingsConfirmed()
        testScope.advanceUntilIdle()

        // User enables only Camera, leaves Microphone denied
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        handler.refreshAllStatuses()
        testScope.advanceUntilIdle()

        // Verify: Only Camera granted
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.grantedGrants.contains(AppGrant.CAMERA))
            assertFalse(state.grantedGrants.contains(AppGrant.MICROPHONE))
            assertEquals(1, state.grantedGrants.size)
        }
    }

    // ==================== Edge Cases and Error Recovery ====================

    @Test
    fun `Dialog dismissal during multi-permission flow`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        var callbackInvoked = false

        handler.request { callbackInvoked = true }
        testScope.advanceUntilIdle()

        // Rationale shown for Camera
        handler.state.test {
            assertTrue(awaitItem().showRationale)
        }

        // User dismisses dialog without action
        handler.onDismiss()
        testScope.advanceUntilIdle()

        // Verify: Dialog hidden, callback not invoked
        handler.state.test {
            val state = awaitItem()
            assertFalse(state.isVisible)
            assertFalse(state.showRationale)
            assertFalse(state.showSettingsGuide)
        }

        assertFalse(callbackInvoked, "Callback should not be invoked")
    }

    @Test
    fun `Three permissions with middle one denied`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION),
            scope = testScope
        )

        var allGranted = false

        handler.request { allGranted = true }
        testScope.advanceUntilIdle()

        // Verify: Camera granted, Microphone denied, Location never requested
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CAMERA))
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.MICROPHONE))
        assertFalse(mockGrantManager.isRequestCalled(AppGrant.LOCATION))
        assertFalse(allGranted, "Callback should not be invoked")

        // Verify granted set has only Camera
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.grantedGrants.contains(AppGrant.CAMERA))
            assertFalse(state.grantedGrants.contains(AppGrant.MICROPHONE))
            assertFalse(state.grantedGrants.contains(AppGrant.LOCATION))
        }
    }

    @Test
    fun `Multiple sequential request calls should work independently`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        // First request
        var firstCallbackInvoked = false
        handler.request { firstCallbackInvoked = true }
        testScope.advanceUntilIdle()

        assertTrue(firstCallbackInvoked)

        // Second request (should work immediately since already granted)
        var secondCallbackInvoked = false
        handler.request { secondCallbackInvoked = true }
        testScope.advanceUntilIdle()

        assertTrue(secondCallbackInvoked)
    }

    @Test
    fun `Progress tracking - grantedGrants updates correctly`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.LOCATION, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION),
            scope = testScope
        )

        handler.state.test {
            skipItems(1) // Skip initial

            handler.request { }
            testScope.advanceUntilIdle()

            // Progress: 1/3
            val state1 = awaitItem()
            assertEquals(1, state1.grantedGrants.size)
            assertEquals(3, state1.totalGrants)

            // Progress: 2/3
            val state2 = awaitItem()
            assertEquals(2, state2.grantedGrants.size)

            // Progress: 3/3 (complete)
            val state3 = awaitItem()
            assertEquals(3, state3.grantedGrants.size)
        }
    }

    // ==================== Real-World Use Case: Contact Sync ====================

    @Test
    fun `Contact sync flow - Contacts and Storage permissions`() = runTest {
        mockGrantManager.setStatus(AppGrant.CONTACTS, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setStatus(AppGrant.STORAGE, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CONTACTS, GrantStatus.GRANTED)
        mockGrantManager.setRequestResult(AppGrant.STORAGE, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CONTACTS, AppGrant.STORAGE),
            scope = testScope
        )

        var contactSyncStarted = false

        handler.request(
            rationaleMessages = mapOf(
                AppGrant.CONTACTS to "Access contacts for sync",
                AppGrant.STORAGE to "Store contact backups"
            )
        ) {
            contactSyncStarted = true
        }
        testScope.advanceUntilIdle()

        // Verify: Both granted, sync started
        assertTrue(contactSyncStarted)
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.CONTACTS))
        assertTrue(mockGrantManager.isRequestCalled(AppGrant.STORAGE))
    }

    @Test
    fun `Refresh status after user changes permissions in Settings`() = runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = testScope
        )

        testScope.advanceUntilIdle()

        // User disables Camera in Settings (external to app)
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)

        handler.refreshAllStatuses()
        testScope.advanceUntilIdle()

        // Verify: Status updated to reflect Settings change
        handler.statuses.test {
            val statuses = awaitItem()
            assertEquals(GrantStatus.DENIED, statuses[AppGrant.CAMERA])
            assertEquals(GrantStatus.GRANTED, statuses[AppGrant.MICROPHONE])
        }

        handler.state.test {
            val state = awaitItem()
            assertEquals(1, state.grantedGrants.size)
            assertTrue(state.grantedGrants.contains(AppGrant.MICROPHONE))
            assertFalse(state.grantedGrants.contains(AppGrant.CAMERA))
        }
    }
}
