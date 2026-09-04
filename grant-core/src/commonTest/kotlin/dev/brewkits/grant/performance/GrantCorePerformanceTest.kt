package dev.brewkits.grant.performance

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantAndServiceHandler
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.fakes.FakeGrantManager
import dev.brewkits.grant.fakes.FakeServiceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance tests for `grant-core`'s public surface at scale.
 *
 * Like the optional modules' `performance/` suites, these use `TestScope` virtual time to
 * verify operations complete within expected bounds without deadlock, stack overflow, or
 * memory exhaustion — they do not measure wall-clock time, which is out of scope for
 * `commonTest`.
 *
 * `grant-core` is the one module with [GrantGroupHandler] and [GrantAndServiceHandler] on its
 * surface (the optional modules only exercise [GrantHandler]), so this suite covers those
 * classes at scale as well, which the per-module `performance/` template does not.
 *
 * This directory did not exist before 2026-09 — see the note in `system/GrantCoreSystemTest.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantCorePerformanceTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `1000 sequential checkStatus calls across all 20 AppGrant values complete`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val allGrants = AppGrant.entries
        val handlers = allGrants.map { GrantHandler(manager, it, this) }

        repeat(50) {
            handlers.forEach { it.refreshStatus() }
        }
        advanceUntilIdle()

        handlers.forEach { handler ->
            assertEquals(GrantStatus.GRANTED, handler.status.value)
        }
    }

    @Test
    fun `1000 parallel requestSuspend calls all resolve without deadlock`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CAMERA, this)

        val results = (1..1000).map { async { handler.requestSuspend() } }.awaitAll()

        assertEquals(1000, results.size)
        results.forEach { status ->
            assertTrue(
                status == GrantStatus.GRANTED || status == GrantStatus.NOT_DETERMINED,
                "Unexpected status: $status",
            )
        }
    }

    @Test
    fun `100 GrantGroupHandler instances with 5 grants each all resolve`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val grants = listOf(
            AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION,
            AppGrant.CONTACTS, AppGrant.NOTIFICATION,
        )

        val handlers = (1..100).map {
            val fake = FakeGrantManager().apply {
                mockStatus = GrantStatus.GRANTED
                mockRequestResult = GrantStatus.GRANTED
            }
            GrantGroupHandler(fake, grants, this)
        }

        var completedCount = 0
        handlers.forEach { it.request { completedCount++ } }
        advanceUntilIdle()

        assertEquals(100, completedCount, "every independent group must reach onAllGranted exactly once")
    }

    @Test
    fun `500 GrantAndServiceHandler ready-checks complete without deadlock`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val serviceManager = FakeServiceManager().apply { mockStatus = ServiceStatus.ENABLED }

        val handler = GrantAndServiceHandler(
            grantManager = manager,
            serviceManager = serviceManager,
            grant = AppGrant.LOCATION,
            serviceType = ServiceType.LOCATION_GPS,
            scope = this,
        )

        var readyCount = 0
        repeat(500) { handler.request { readyCount++ } }
        advanceUntilIdle()

        assertTrue(readyCount >= 1, "at least the first ready callback should fire")
    }

    @Test
    fun `creating 200 independent handlers across every AppGrant does not corrupt shared state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED

        val handlers = (1..200).map { i ->
            val grant = AppGrant.entries[i % AppGrant.entries.size]
            val fake = FakeGrantManager().apply { mockStatus = GrantStatus.GRANTED }
            grant to GrantHandler(fake, grant, this)
        }

        handlers.forEach { (_, handler) -> handler.refreshStatus() }
        advanceUntilIdle()

        handlers.forEach { (grant, handler) ->
            assertEquals(GrantStatus.GRANTED, handler.status.value, "handler for $grant must independently track GRANTED")
        }
    }
}
