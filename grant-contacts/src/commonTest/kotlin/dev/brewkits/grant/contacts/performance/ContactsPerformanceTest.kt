package dev.brewkits.grant.contacts.performance

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.contacts.fakes.FakeGrantManager
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
 * Performance tests for Contacts permission operations.
 *
 * These tests use TestScope virtual time to verify that operations complete
 * within expected iteration bounds without stack overflow or memory exhaustion.
 * They do NOT measure wall-clock time — that is out of scope for common tests.
 *
 * Key invariants validated:
 * - 1000 sequential checkStatus calls complete without OOM
 * - 1000 parallel requestSuspend calls all resolve
 * - Handler creation at scale does not leak memory (GC cannot be forced in tests,
 *   but creating 1000 instances without error validates no global state leak)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsPerformanceTest {

    private lateinit var manager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `1000 sequential checkStatus calls all complete`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        repeat(1000) { handler.refreshStatus() }
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }

    @Test
    fun `1000 parallel requestSuspend calls for GRANTED permission all resolve`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        val results = (1..1000).map { async { handler.requestSuspend() } }.awaitAll()

        assertEquals(1000, results.size)
        results.forEach { status ->
            assertTrue(
                status == GrantStatus.GRANTED || status == GrantStatus.NOT_DETERMINED,
                "Unexpected status: $status"
            )
        }
    }

    @Test
    fun `creating 100 independent CONTACTS handlers does not corrupt shared state`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED

        val handlers = (1..100).map {
            GrantHandler(FakeGrantManager(mockStatus = GrantStatus.GRANTED), AppGrant.CONTACTS, this)
        }

        handlers.forEach { it.refreshStatus() }
        advanceUntilIdle()

        handlers.forEach { handler ->
            assertEquals(GrantStatus.GRANTED, handler.status.value,
                "Each handler should independently track GRANTED status")
        }
    }

    @Test
    fun `large batch request CONTACTS + READ_CONTACTS 500 times completes`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        var totalCallbacks = 0
        val contactsHandler = GrantHandler(manager, AppGrant.CONTACTS, this)
        val readContactsHandler = GrantHandler(manager, AppGrant.READ_CONTACTS, this)

        repeat(500) {
            contactsHandler.request { totalCallbacks++ }
            readContactsHandler.request { totalCallbacks++ }
        }
        advanceUntilIdle()

        assertTrue(totalCallbacks >= 2, "At least one callback per handler must fire")
    }
}
