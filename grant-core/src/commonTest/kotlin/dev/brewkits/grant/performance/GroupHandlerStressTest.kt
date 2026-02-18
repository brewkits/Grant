package dev.brewkits.grant.performance

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
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Stress tests for GrantGroupHandler.
 *
 * Tests performance with multiple permissions:
 * - Large permission groups
 * - Sequential request performance
 * - Status refresh for many permissions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupHandlerStressTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = MultiGrantFakeManager()

    @Test
    fun `Group with 2 permissions completes quickly`() = runTest {
        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)

        grants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.NOT_DETERMINED)
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = grants,
            scope = testScope
        )

        var completed = false

        val duration = measureTime {
            handler.request { completed = true }
            testScope.advanceUntilIdle()
        }

        assertTrue(completed, "Request should complete")
        println("Group with 2 permissions completed in: $duration")
    }

    @Test
    fun `Group with 5 permissions performs well`() = runTest {
        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.LOCATION,
            AppGrant.NOTIFICATION,
            AppGrant.CONTACTS
        )

        grants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.NOT_DETERMINED)
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = grants,
            scope = testScope
        )

        var completed = false

        val duration = measureTime {
            handler.request { completed = true }
            testScope.advanceUntilIdle()
        }

        assertTrue(completed, "All 5 permissions should be granted")
        println("Group with 5 permissions completed in: $duration")
    }

    @Test
    fun `Group with all 14 permissions handles efficiently`() = runTest {
        val grants = listOf(
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

        grants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.NOT_DETERMINED)
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = grants,
            scope = testScope
        )

        var completed = false

        val duration = measureTime {
            handler.request { completed = true }
            testScope.advanceUntilIdle()
        }

        assertTrue(completed, "All 14 permissions should be granted")
        println("Group with all 14 permissions completed in: $duration")
    }

    @Test
    fun `Refresh status for large permission group is fast`() = runTest {
        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.LOCATION,
            AppGrant.NOTIFICATION,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.STORAGE
        )

        grants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.GRANTED)
        }

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = grants,
            scope = testScope
        )

        val duration = measureTime {
            repeat(50) {
                handler.refreshAllStatuses()
                testScope.advanceUntilIdle()
            }
        }

        println("50 status refreshes for 7 permissions completed in: $duration")
    }

    @Test
    fun `Multiple group handlers can coexist`() = runTest {
        val videoGrants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        val locationGrants = listOf(AppGrant.LOCATION, AppGrant.CAMERA)
        val socialGrants = listOf(AppGrant.CONTACTS, AppGrant.STORAGE)

        listOf(videoGrants, locationGrants, socialGrants).flatten().toSet().forEach {
            mockGrantManager.setStatus(it, GrantStatus.NOT_DETERMINED)
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }

        val handlers = listOf(
            GrantGroupHandler(mockGrantManager, videoGrants, testScope),
            GrantGroupHandler(mockGrantManager, locationGrants, testScope),
            GrantGroupHandler(mockGrantManager, socialGrants, testScope)
        )

        val duration = measureTime {
            handlers.forEach { handler ->
                handler.refreshAllStatuses()
            }
            testScope.advanceUntilIdle()
        }

        println("Created and refreshed 3 group handlers in: $duration")
    }

    @Test
    fun `Stress test - Sequential requests to group handler`() = runTest {
        val grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)

        grants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.GRANTED)
        }

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = grants,
            scope = testScope
        )

        var completedCount = 0

        val duration = measureTime {
            repeat(100) {
                handler.request { completedCount++ }
                testScope.advanceUntilIdle()
            }
        }

        assertTrue(completedCount == 100, "All 100 requests should complete")
        println("100 sequential group requests completed in: $duration")
    }

    @Test
    fun `Performance scales with group size`() = runTest {
        // Test with 2 permissions
        val smallGrants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
        smallGrants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.NOT_DETERMINED)
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }

        val smallHandler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = smallGrants,
            scope = testScope
        )

        val smallDuration = measureTime {
            smallHandler.request { }
            testScope.advanceUntilIdle()
        }

        // Test with 6 permissions
        val largeGrants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.LOCATION,
            AppGrant.NOTIFICATION,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR
        )
        largeGrants.forEach {
            mockGrantManager.setStatus(it, GrantStatus.NOT_DETERMINED)
            mockGrantManager.setRequestResult(it, GrantStatus.GRANTED)
        }

        val largeHandler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = largeGrants,
            scope = testScope
        )

        val largeDuration = measureTime {
            largeHandler.request { }
            testScope.advanceUntilIdle()
        }

        println("Group with 2 permissions: $smallDuration")
        println("Group with 6 permissions: $largeDuration")
        println("Scaling factor: ${largeDuration.inWholeMilliseconds.toDouble() / smallDuration.inWholeMilliseconds.toDouble()}x")

        // Should scale roughly linearly (3x time for 3x permissions)
    }

    @Test
    fun `Partial grant handling is efficient`() = runTest {
        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.LOCATION
        )

        // Camera and Microphone granted, Location denied
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)
        mockGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.DENIED)

        val handler = GrantGroupHandler(
            grantManager = mockGrantManager,
            grants = grants,
            scope = testScope
        )

        var completed = false

        val duration = measureTime {
            handler.request { completed = true }
            testScope.advanceUntilIdle()
        }

        // Should not complete (Location denied)
        assertTrue(!completed, "Should not complete with one denied permission")
        println("Partial grant check (2 granted, 1 denied) completed in: $duration")
    }
}
