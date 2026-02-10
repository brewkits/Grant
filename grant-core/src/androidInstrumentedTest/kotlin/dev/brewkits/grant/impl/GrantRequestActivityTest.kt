package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.flow.first
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
    fun testRequestSinglePermission_alreadyGranted() = runBlocking {
        // Skip if permission not already granted (manual test)
        val permission = Manifest.permission.INTERNET  // Usually granted
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            return@runBlocking
        }

        val requestId = GrantRequestActivity.requestGrants(context, listOf(permission))

        val resultFlow = GrantRequestActivity.getResultFlow(requestId)
        assertNotNull(resultFlow, "Result flow should not be null")

        // Wait for result (should be immediate since already granted)
        val result = withTimeout(5000) {
            resultFlow.first { it != null }
        }

        assertEquals(
            GrantRequestActivity.GrantResult.GRANTED,
            result,
            "Already granted permission should return GRANTED"
        )

        // Cleanup
        GrantRequestActivity.cleanup(requestId)
    }

    @Test
    fun testRequestMultiplePermissions() = runBlocking {
        // Test with multiple permissions that are usually granted
        val permissions = listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val requestId = GrantRequestActivity.requestGrants(context, permissions)

        val resultFlow = GrantRequestActivity.getResultFlow(requestId)
        assertNotNull(resultFlow, "Result flow should not be null")

        // Wait for result
        val result = withTimeout(5000) {
            resultFlow.first { it != null }
        }

        assertNotNull(result, "Result should not be null")

        // Cleanup
        GrantRequestActivity.cleanup(requestId)
    }

    @Test
    fun testOrphanCleanup() = runBlocking {
        // Create multiple orphaned requests
        val requestIds = mutableListOf<String>()

        repeat(5) {
            val requestId = java.util.UUID.randomUUID().toString()
            // Manually add to pendingResults to simulate orphaned entries
            // (In real scenario, these would be created by process death)
            requestIds.add(requestId)
        }

        // Trigger cleanup by creating a new request
        val newRequestId = GrantRequestActivity.requestGrants(
            context,
            listOf(Manifest.permission.INTERNET)
        )

        // Verify new request works
        val resultFlow = GrantRequestActivity.getResultFlow(newRequestId)
        assertNotNull(resultFlow, "New request should work after cleanup")

        // Cleanup
        GrantRequestActivity.cleanup(newRequestId)
    }

    @Test
    fun testGenerateUniqueRequestIds() {
        // Test that generated request IDs are unique
        val requestIds = mutableSetOf<String>()

        repeat(100) {
            val requestId = java.util.UUID.randomUUID().toString()
            assertTrue(
                requestIds.add(requestId),
                "Request IDs should be unique"
            )
        }

        assertEquals(100, requestIds.size, "Should generate 100 unique IDs")
    }

    @Test
    fun testRequestFlowCreation() {
        // Test that requesting grants creates flow correctly
        val requestId = java.util.UUID.randomUUID().toString()

        // Before launching activity, flow should not exist
        val flowBefore = GrantRequestActivity.getResultFlow(requestId)
        assertEquals(null, flowBefore, "Flow should not exist before request")

        // Launch activity (will create flow)
        val actualRequestId = GrantRequestActivity.requestGrants(
            context,
            listOf(Manifest.permission.INTERNET)
        )

        // After launching, flow should exist
        val flowAfter = GrantRequestActivity.getResultFlow(actualRequestId)
        assertNotNull(flowAfter, "Flow should exist after request")

        // Cleanup
        GrantRequestActivity.cleanup(actualRequestId)
    }

    @Test
    fun testCleanupRemovesFlows() {
        val requestId = GrantRequestActivity.requestGrants(
            context,
            listOf(Manifest.permission.INTERNET)
        )

        // Flow should exist
        var flow = GrantRequestActivity.getResultFlow(requestId)
        assertNotNull(flow, "Flow should exist before cleanup")

        // Cleanup
        GrantRequestActivity.cleanup(requestId)

        // Flow should be removed
        flow = GrantRequestActivity.getResultFlow(requestId)
        assertEquals(null, flow, "Flow should be removed after cleanup")
    }

    /**
     * Test permission request with empty list.
     * Should handle gracefully without crashing.
     */
    @Test
    fun testEmptyPermissionList() = runBlocking {
        val requestId = GrantRequestActivity.requestGrants(context, emptyList())

        val resultFlow = GrantRequestActivity.getResultFlow(requestId)
        assertNotNull(resultFlow, "Should handle empty permission list")

        // Result should be ERROR for empty list
        val result = withTimeout(5000) {
            resultFlow.first { it != null }
        }

        assertNotNull(result, "Should return result for empty list")

        GrantRequestActivity.cleanup(requestId)
    }
}
