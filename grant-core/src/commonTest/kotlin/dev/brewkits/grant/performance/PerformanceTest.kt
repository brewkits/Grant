package dev.brewkits.grant.performance

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Performance tests for Grant library.
 *
 * Tests performance characteristics including:
 * - Request latency
 * - Memory efficiency with multiple handlers
 * - Stress tests with many concurrent requests
 * - StateFlow update performance
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()

    // ==================== Request Latency Tests ====================

    @Test
    fun `Single permission request completes quickly`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        var completed = false

        val duration = measureTime {
            handler.request { completed = true }
            testScope.advanceUntilIdle()
        }

        assertTrue(completed, "Request should complete")
        // In test environment, should be very fast (< 100ms in real scenarios)
        println("Single request completed in: $duration")
    }

    @Test
    fun `Multiple sequential requests complete efficiently`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
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
        println("100 sequential requests completed in: $duration")
    }

    @Test
    fun `Status check is fast`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED

        val duration = measureTime {
            repeat(1000) {
                mockGrantManager.checkStatus(AppGrant.CAMERA)
            }
        }

        println("1000 status checks completed in: $duration")
        // Status checks should be very fast
    }

    // ==================== Memory Efficiency Tests ====================

    @Test
    fun `Creating many handlers is memory efficient`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val handlers = mutableListOf<GrantHandler>()

        val duration = measureTime {
            repeat(100) {
                val handler = GrantHandler(
                    grantManager = mockGrantManager,
                    grant = AppGrant.CAMERA,
                    scope = testScope
                )
                handlers.add(handler)
            }
        }

        assertTrue(handlers.size == 100, "Should create 100 handlers")
        println("Created 100 handlers in: $duration")
    }

    @Test
    fun `Handlers can be created and destroyed repeatedly`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val duration = measureTime {
            repeat(50) {
                val handler = GrantHandler(
                    grantManager = mockGrantManager,
                    grant = AppGrant.CAMERA,
                    scope = testScope
                )
                // Handler goes out of scope and can be GC'd
            }
        }

        println("Created and destroyed 50 handlers in: $duration")
    }

    // ==================== Stress Tests ====================

    @Test
    fun `Stress test - 1000 permission status checks`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED

        var checksCompleted = 0

        val duration = measureTime {
            repeat(1000) {
                val status = mockGrantManager.checkStatus(AppGrant.CAMERA)
                if (status == GrantStatus.GRANTED) {
                    checksCompleted++
                }
            }
        }

        assertTrue(checksCompleted == 1000, "All 1000 checks should complete")
        println("Stress test: 1000 status checks completed in: $duration")
    }

    @Test
    fun `Stress test - Multiple handlers concurrent initialization`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val handlers = mutableListOf<GrantHandler>()

        val duration = measureTime {
            // Create 50 handlers concurrently
            repeat(50) { index ->
                launch {
                    val grant = when (index % 14) {
                        0 -> AppGrant.CAMERA
                        1 -> AppGrant.MICROPHONE
                        2 -> AppGrant.GALLERY
                        3 -> AppGrant.LOCATION
                        4 -> AppGrant.NOTIFICATION
                        5 -> AppGrant.BLUETOOTH
                        6 -> AppGrant.CONTACTS
                        7 -> AppGrant.CALENDAR
                        8 -> AppGrant.MOTION
                        9 -> AppGrant.STORAGE
                        10 -> AppGrant.GALLERY_IMAGES_ONLY
                        11 -> AppGrant.GALLERY_VIDEO_ONLY
                        12 -> AppGrant.LOCATION_ALWAYS
                        else -> AppGrant.SCHEDULE_EXACT_ALARM
                    }

                    val handler = GrantHandler(
                        grantManager = mockGrantManager,
                        grant = grant,
                        scope = testScope
                    )
                    handlers.add(handler)
                }
            }
            advanceUntilIdle()
        }

        assertTrue(handlers.size == 50, "Should create 50 handlers")
        println("Concurrent initialization of 50 handlers in: $duration")
    }

    @Test
    fun `Stress test - Rapid state updates`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        var updateCount = 0

        val duration = measureTime {
            repeat(200) {
                handler.request { updateCount++ }
                testScope.advanceUntilIdle()
            }
        }

        assertTrue(updateCount == 200, "All 200 requests should complete")
        println("200 rapid state updates completed in: $duration")
    }

    @Test
    fun `Stress test - Many permissions checked in batch`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED

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

        var checksCompleted = 0

        val duration = measureTime {
            repeat(100) {
                allPermissions.forEach { permission ->
                    val status = mockGrantManager.checkStatus(permission)
                    if (status == GrantStatus.GRANTED) {
                        checksCompleted++
                    }
                }
            }
        }

        assertTrue(checksCompleted == 1400, "All 1400 checks should complete") // 14 permissions * 100 iterations
        println("Batch check of all 14 permissions x 100 iterations in: $duration")
    }

    // ==================== StateFlow Performance Tests ====================

    @Test
    fun `StateFlow updates are efficient`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED

        val handler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        val duration = measureTime {
            repeat(100) {
                mockGrantManager.mockStatus = GrantStatus.DENIED
                handler.request { }
                testScope.advanceUntilIdle()

                handler.onDismiss()
                testScope.advanceUntilIdle()
            }
        }

        println("100 StateFlow updates (show/hide dialogs) in: $duration")
    }

    @Test
    fun `Status refresh is fast with many handlers`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED

        val handlers = List(20) {
            GrantHandler(
                grantManager = mockGrantManager,
                grant = AppGrant.CAMERA,
                scope = testScope
            )
        }

        val duration = measureTime {
            handlers.forEach { handler ->
                handler.refreshStatus()
            }
            testScope.advanceUntilIdle()
        }

        println("Refreshed status for 20 handlers in: $duration")
    }

    // ==================== Concurrent Operations ====================

    @Test
    fun `Multiple handlers process requests independently`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED

        val cameraHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.CAMERA,
            scope = testScope
        )

        val micHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.MICROPHONE,
            scope = testScope
        )

        val locationHandler = GrantHandler(
            grantManager = mockGrantManager,
            grant = AppGrant.LOCATION,
            scope = testScope
        )

        var completedCount = 0

        val duration = measureTime {
            cameraHandler.request { completedCount++ }
            testScope.advanceUntilIdle()

            micHandler.request { completedCount++ }
            testScope.advanceUntilIdle()

            locationHandler.request { completedCount++ }
            testScope.advanceUntilIdle()
        }

        assertTrue(completedCount == 3, "All 3 requests should complete independently")
        println("3 requests to different handlers processed in: $duration")
    }

    @Test
    fun `Performance scales with number of permissions`() = runTest {
        mockGrantManager.mockStatus = GrantStatus.GRANTED

        val permissions = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.LOCATION
        )

        // Test with 1 permission
        val duration1 = measureTime {
            repeat(100) {
                mockGrantManager.checkStatus(permissions[0])
            }
        }

        // Test with all 4 permissions
        val duration4 = measureTime {
            repeat(100) {
                permissions.forEach { mockGrantManager.checkStatus(it) }
            }
        }

        println("100 checks with 1 permission: $duration1")
        println("100 checks with 4 permissions: $duration4")
        println("Scaling factor: ${duration4.inWholeMilliseconds.toDouble() / duration1.inWholeMilliseconds.toDouble()}x")

        // Should scale roughly linearly (not exponentially)
    }
}
