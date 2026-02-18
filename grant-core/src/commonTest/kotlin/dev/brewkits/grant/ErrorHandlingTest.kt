package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for error handling scenarios across Grant library.
 *
 * Covers:
 * - Permission denial scenarios
 * - State transitions on errors
 * - Recovery from errors
 * - Edge case handling
 */
class ErrorHandlingTest {

    @Test
    fun `request with DENIED status should allow retry`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.DENIED

        val result1 = manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED, result1)

        // User should be able to retry
        manager.mockRequestResult = GrantStatus.GRANTED
        val result2 = manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, result2)
    }

    @Test
    fun `request with DENIED_ALWAYS should not retry automatically`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        val result = manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED_ALWAYS, result)
        // User must go to Settings to enable
    }

    @Test
    fun `checkStatus should handle NOT_DETERMINED state`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.NOT_DETERMINED

        val status = manager.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    @Test
    fun `checkStatus should handle GRANTED state`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.GRANTED

        val status = manager.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, status)
    }

    @Test
    fun `checkStatus should handle DENIED state`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.DENIED

        val status = manager.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    fun `checkStatus should handle DENIED_ALWAYS state`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.DENIED_ALWAYS

        val status = manager.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }

    @Test
    fun `request after DENIED should show rationale`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.DENIED
        manager.mockRequestResult = GrantStatus.DENIED

        val result = manager.request(AppGrant.CAMERA)
        // After DENIED, app should show rationale before retry
        assertEquals(GrantStatus.DENIED, result)
    }

    @Test
    fun `request after DENIED_ALWAYS should direct to Settings`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        val result = manager.request(AppGrant.CAMERA)
        // After DENIED_ALWAYS, app should direct user to Settings
        assertEquals(GrantStatus.DENIED_ALWAYS, result)
    }

    @Test
    fun `multiple requests for same permission should be consistent`() = runTest {
        val manager = FakeGrantManager()
        manager.mockStatus = GrantStatus.GRANTED

        val result1 = manager.checkStatus(AppGrant.CAMERA)
        val result2 = manager.checkStatus(AppGrant.CAMERA)
        val result3 = manager.checkStatus(AppGrant.CAMERA)

        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `different permissions should have independent states`() = runTest {
        val manager = FakeGrantManager()

        // Camera granted
        manager.mockStatus = GrantStatus.GRANTED
        val cameraStatus = manager.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, cameraStatus)

        // Microphone denied (independent)
        manager.mockStatus = GrantStatus.DENIED
        val micStatus = manager.checkStatus(AppGrant.MICROPHONE)
        assertEquals(GrantStatus.DENIED, micStatus)
    }

    @Test
    fun `openSettings should not throw exceptions`() {
        val manager = FakeGrantManager()
        // openSettings should handle errors gracefully
        manager.openSettings() // Should complete without crash
        assertTrue(true, "openSettings completed without exception")
    }

    @Test
    fun `permission state transitions should be valid`() = runTest {
        val manager = FakeGrantManager()

        // Valid transition: NOT_DETERMINED -> GRANTED
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val result1 = manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, result1)

        // Valid transition: NOT_DETERMINED -> DENIED
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.DENIED
        val result2 = manager.request(AppGrant.MICROPHONE)
        assertEquals(GrantStatus.DENIED, result2)

        // Valid transition: DENIED -> GRANTED (after user enables in Settings)
        manager.mockStatus = GrantStatus.DENIED
        manager.mockRequestResult = GrantStatus.GRANTED
        val result3 = manager.request(AppGrant.LOCATION)
        assertEquals(GrantStatus.GRANTED, result3)
    }

    @Test
    fun `RawPermission with empty permissions list should be handled`() = runTest {
        val manager = FakeGrantManager()
        val rawPermission = RawPermission(
            identifier = "EMPTY",
            androidPermissions = emptyList(),
            iosUsageKey = null
        )

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val status = manager.checkStatus(rawPermission)
        // Should not crash with empty permissions
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }

    @Test
    fun `RawPermission with null iOS key should be handled`() = runTest {
        val manager = FakeGrantManager()
        val rawPermission = RawPermission(
            identifier = "NO_IOS",
            androidPermissions = listOf("android.permission.CUSTOM"),
            iosUsageKey = null
        )

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val status = manager.checkStatus(rawPermission)
        // Should handle null iOS key gracefully
        assertEquals(GrantStatus.NOT_DETERMINED, status)
    }
}
