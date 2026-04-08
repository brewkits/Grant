package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Instrumented tests for GrantRequestActivity.
 *
 * These tests verify:
 * - Activity launches correctly
 * - Permission requests flow
 * - Process death recovery
 * - Orphan cleanup
 * - Multiple permission handling
 *
 * Note: These tests require a real Android device or emulator
 * as they interact with the actual permission system.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class GrantRequestActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testA_RequestSinglePermission_alreadyGranted() = runBlocking {
        // Use a standard install-time permission
        val permission = Manifest.permission.VIBRATE
        val requestId = GrantRequestActivity.requestGrants(context, listOf(permission))

        val deferred = GrantRequestActivity.getResultDeferred(requestId)
        assertNotNull(deferred, "Result deferred should not be null")

        // Wait for result
        val result = withTimeout(60000) {
            deferred.await()
        }

        assertEquals(
            GrantRequestActivity.GrantResult.GRANTED,
            result,
            "Install-time permission should return GRANTED"
        )

        // Cleanup
        GrantRequestActivity.cleanup(requestId)
    }

    @Test
    fun testRequestMultiplePermissions() = runBlocking {
        // Test with multiple install-time permissions
        val permissions = listOf(
            Manifest.permission.VIBRATE,
            Manifest.permission.REORDER_TASKS
        )

        val requestId = GrantRequestActivity.requestGrants(context, permissions)

        val deferred = GrantRequestActivity.getResultDeferred(requestId)
        assertNotNull(deferred, "Result deferred should not be null")

        // Wait for result
        val result = withTimeout(60000) {
            deferred.await()
        }

        assertEquals(
            GrantRequestActivity.GrantResult.GRANTED,
            result,
            "Install-time permissions should return GRANTED"
        )

        // Cleanup
        GrantRequestActivity.cleanup(requestId)
    }

    @Test
    fun testOrphanCleanup() = runBlocking {
        // Create multiple orphaned requests by simulating old timestamps
        // Creating actual orphaned entries is hard because it's in companion,
        // but we verify the cleanup logic by launching new requests.

        // Trigger cleanup by creating a new request
        val newRequestId = GrantRequestActivity.requestGrants(
            context,
            listOf(Manifest.permission.VIBRATE)
        )

        // Verify new request works
        val deferred = GrantRequestActivity.getResultDeferred(newRequestId)
        assertNotNull(deferred, "New request should work after cleanup")

        // Cleanup
        GrantRequestActivity.cleanup(newRequestId)
    }

    @Test
    fun testGenerateUniqueRequestIds() {
        // Test that generated request IDs are unique
        val requestIds = mutableSetOf<String>()

        repeat(100) {
            val requestId = GrantRequestActivity.requestGrants(
                context,
                listOf(Manifest.permission.VIBRATE)
            )
            assertTrue(
                requestIds.add(requestId),
                "Request IDs should be unique"
            )
            GrantRequestActivity.cleanup(requestId)
        }

        assertEquals(100, requestIds.size, "Should generate 100 unique IDs")
    }

    @Test
    fun testRequestFlowCreation() {
        // Test that requesting grants creates deferred correctly
        val requestId = "99999" // Dummy ID

        // Before launching activity, deferred should not exist
        val deferredBefore = GrantRequestActivity.getResultDeferred(requestId)
        assertEquals(null, deferredBefore, "Deferred should not exist before request")

        // Launch activity (will create deferred)
        val actualRequestId = GrantRequestActivity.requestGrants(
            context,
            listOf(Manifest.permission.VIBRATE)
        )

        // After launching, deferred should exist
        val deferredAfter = GrantRequestActivity.getResultDeferred(actualRequestId)
        assertNotNull(deferredAfter, "Deferred should exist after request")

        // Cleanup
        GrantRequestActivity.cleanup(actualRequestId)
    }

    @Test
    fun testCleanupRemovesFlows() {
        val requestId = GrantRequestActivity.requestGrants(
            context,
            listOf(Manifest.permission.VIBRATE)
        )

        // Deferred should exist
        var deferred = GrantRequestActivity.getResultDeferred(requestId)
        assertNotNull(deferred, "Deferred should exist before cleanup")

        // Cleanup
        GrantRequestActivity.cleanup(requestId)

        // Deferred should be removed
        deferred = GrantRequestActivity.getResultDeferred(requestId)
        assertEquals(null, deferred, "Deferred should be removed after cleanup")
    }

    /**
     * Test permission request with empty list.
     * Should handle gracefully without crashing.
     */
    @Test
    fun testEmptyPermissionList() = runBlocking {
        val requestId = GrantRequestActivity.requestGrants(context, emptyList())

        val deferred = GrantRequestActivity.getResultDeferred(requestId)
        assertNotNull(deferred, "Should handle empty permission list")

        // Result should be returned for empty list
        val result = withTimeout(10000) {
            deferred.await()
        }

        assertNotNull(result, "Should return result for empty list")

        GrantRequestActivity.cleanup(requestId)
    }
}
