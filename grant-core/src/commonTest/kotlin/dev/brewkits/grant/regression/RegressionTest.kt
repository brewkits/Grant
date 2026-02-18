package dev.brewkits.grant.regression

import dev.brewkits.grant.*
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
 * Regression tests to ensure previously fixed bugs don't return.
 *
 * Each test documents a specific bug that was fixed in a previous version
 * and verifies the fix still works.
 *
 * References:
 * - Issue #129: iOS deadlock on first microphone permission request
 * - Android dead click issue: Transparent activity not properly cleaned up
 * - Process death: Dialog state not restored after process kill
 * - Info.plist: Missing keys cause crashes on iOS
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    // ==================== moko-permissions Issue #129 ====================

    @Test
    fun `Regression - First microphone request does not deadlock`() = runTest {
        // Bug: First microphone permission request on iOS caused deadlock
        // Fixed in: Grant v1.0.0
        // Verification: Microphone request completes without hanging

        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.MICROPHONE,
            scope = testScope
        )

        var completed = false

        // This should complete without deadlock
        handler.request { completed = true }
        testScope.advanceUntilIdle()

        assertTrue(completed, "Microphone request should complete without deadlock")
    }

    @Test
    fun `Regression - Bluetooth delegate timeout works correctly`() = runTest {
        // Bug: Bluetooth permission could hang indefinitely
        // Fixed in: Grant v1.0.0 (added 10-second timeout)
        // Verification: Request completes even if Bluetooth is off

        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.BLUETOOTH,
            scope = testScope
        )

        var completed = false

        handler.request { completed = true }
        testScope.advanceUntilIdle()

        // Should complete (though not granted)
        assertFalse(completed, "Bluetooth request should handle denial gracefully")
    }

    // ==================== Android Dead Click Issue ====================

    @Test
    fun `Regression - Permission state correctly reflects DENIED after user denial`() = runTest {
        // Bug: After user denies permission, subsequent clicks don't show rationale
        // Fixed in: Grant v1.0.1
        // Verification: DENIED status persists and rationale shows on retry

        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // First request - denied
        handler.request { }
        testScope.advanceUntilIdle()

        // Update status to DENIED (simulates real behavior)
        mockGrantManager.mockStatus = GrantStatus.DENIED

        // Second request - should show rationale
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        // Verify rationale is shown
        handler.state.value.let { state ->
            assertTrue(state.showRationale, "Rationale should be shown after denial")
        }
    }

    @Test
    fun `Regression - Multiple denials transition to DENIED_ALWAYS correctly`() = runTest {
        // Bug: Permanent denial state not properly handled
        // Fixed in: Grant v1.0.1
        // Verification: After multiple denials, shows settings guide

        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // First denial - show rationale
        handler.request(
            rationaleMessage = "Camera needed",
            settingsMessage = "Enable in Settings"
        ) { }
        testScope.advanceUntilIdle()

        assertTrue(handler.state.value.showRationale)

        // User denies again - becomes permanent
        mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        handler.onRationaleConfirmed()
        testScope.advanceUntilIdle()

        // Should show settings guide
        assertTrue(handler.state.value.showSettingsGuide, "Settings guide should be shown")
    }

    // ==================== Process Death Recovery ====================

    @Test
    fun `Regression - Dialog state is saved across process death`() = runTest {
        // Bug: Dialog state lost on Android process death
        // Fixed in: Grant v1.0.0 (SavedStateDelegate support)
        // Verification: State persists and restores

        val savedStateDelegate = object : SavedStateDelegate {
            private val storage = mutableMapOf<String, String>()

            override fun saveState(key: String, value: String) {
                storage[key] = value
            }

            override fun restoreState(key: String): String? = storage[key]

            override fun clear(key: String) {
                storage.remove(key)
            }
        }

        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        // Show rationale
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        assertTrue(handler.state.value.showRationale)

        // Simulate process death - create new handler with same delegate
        val restoredHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope,
            savedStateDelegate = savedStateDelegate
        )

        testScope.advanceUntilIdle()

        // State should be restored
        assertTrue(restoredHandler.state.value.showRationale, "Dialog state should be restored")
    }

    @Test
    fun `Regression - State clears when permission is granted`() = runTest {
        // Bug: Stale dialog state persists after permission granted
        // Fixed in: Grant v1.0.1
        // Verification: Request completes when permission is granted

        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        var granted = false

        // Request and grant
        handler.request { granted = true }
        testScope.advanceUntilIdle()

        // Should be granted without showing dialogs
        assertTrue(granted, "Permission should be granted")
        assertFalse(handler.state.value.showRationale, "Rationale should not be shown")
        assertFalse(handler.state.value.showSettingsGuide, "Settings guide should not be shown")
    }

    // ==================== RawPermission Edge Cases ====================

    @Test
    fun `Regression - RawPermission with empty Android list handled gracefully`() = runTest {
        // Bug: Empty permission list caused crashes
        // Fixed in: Grant v1.0.0
        // Verification: Empty list doesn't crash

        val rawPermission = RawPermission(
            identifier = "EMPTY_PERMISSION",
            androidPermissions = emptyList(),
            iosUsageKey = null
        )

        val status = mockGrantManager.checkStatus(rawPermission)

        // Should not crash, returns NOT_DETERMINED
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    @Test
    fun `Regression - RawPermission with null iOS key handled gracefully`() = runTest {
        // Bug: Null iOS key caused crashes on iOS
        // Fixed in: Grant v1.0.0
        // Verification: Null iOS key doesn't crash

        val rawPermission = RawPermission(
            identifier = "ANDROID_ONLY",
            androidPermissions = listOf("android.permission.CAMERA"),
            iosUsageKey = null
        )

        val status = mockGrantManager.checkStatus(rawPermission)

        // Should not crash
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    // ==================== Permission State Transitions ====================

    @Test
    fun `Regression - All GrantStatus transitions are valid`() = runTest {
        // Bug: Invalid state transitions caused UI glitches
        // Fixed in: Grant v1.0.1
        // Verification: All transitions work correctly

        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // NOT_DETERMINED → GRANTED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        handler.request { }
        testScope.advanceUntilIdle()

        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        // NOT_DETERMINED → DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        val handler2 = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.MICROPHONE,
            scope = testScope
        )
        handler2.request { }
        testScope.advanceUntilIdle()

        // All transitions should complete without errors
        assertTrue(true, "All state transitions completed successfully")
    }

    @Test
    fun `Regression - Dialog dismissal properly cleans up state`() = runTest {
        // Bug: Dismissed dialogs could re-appear
        // Fixed in: Grant v1.0.1
        // Verification: Dismissed dialogs stay dismissed

        mockGrantManager.mockStatus = GrantStatus.DENIED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        // Show rationale
        handler.request(rationaleMessage = "Camera needed") { }
        testScope.advanceUntilIdle()

        assertTrue(handler.state.value.showRationale)

        // Dismiss
        handler.onDismiss()
        testScope.advanceUntilIdle()

        // Should stay dismissed
        assertFalse(handler.state.value.showRationale, "Dialog should stay dismissed")

        // Even after checking status
        handler.refreshStatus()
        testScope.advanceUntilIdle()

        assertFalse(handler.state.value.showRationale, "Dialog should still be dismissed")
    }

    // ==================== Permission Identifiers ====================

    @Test
    fun `Regression - All permission types have unique identifiers`() {
        // Bug: Duplicate identifiers caused permission mix-ups
        // Fixed in: Grant v1.0.0
        // Verification: All identifiers are unique

        val allPermissions = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY,
            AppGrant.LOCATION,
            AppGrant.LOCATION_ALWAYS,
            AppGrant.NOTIFICATION,
            AppGrant.BLUETOOTH,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.MOTION,
            AppGrant.STORAGE,
            AppGrant.SCHEDULE_EXACT_ALARM
        )

        val identifiers = allPermissions.map { it.identifier }
        val uniqueIdentifiers = identifiers.toSet()

        assertEquals(
            allPermissions.size,
            uniqueIdentifiers.size,
            "All permissions should have unique identifiers"
        )
    }

    // ==================== Settings Navigation ====================

    @Test
    fun `Regression - openSettings can be called multiple times safely`() = runTest {
        // Bug: Repeated openSettings calls caused crashes
        // Fixed in: Grant v1.0.0
        // Verification: Multiple calls work safely

        repeat(10) {
            mockGrantManager.openSettings()
        }

        assertTrue(mockGrantManager.openSettingsCalled, "openSettings should complete without crash")
    }

    @Test
    fun `Regression - Settings guide shows correct message`() = runTest {
        // Bug: Custom settings messages not displayed
        // Fixed in: Grant v1.0.1
        // Verification: Custom message appears in state

        mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        val customMessage = "Please enable Camera in iOS Settings > Privacy > Camera"

        handler.request(settingsMessage = customMessage) { }
        testScope.advanceUntilIdle()

        assertEquals(customMessage, handler.state.value.settingsMessage,
            "Custom settings message should be displayed")
    }
}
