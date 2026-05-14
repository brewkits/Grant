package dev.brewkits.grant.contacts.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.contacts.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * System tests for Contacts permissions in realistic usage scenarios.
 *
 * Simulates complete end-to-end flows as a real app would use them:
 * - Contact sync requiring CONTACTS (read+write)
 * - Contact picker requiring READ_CONTACTS (read-only)
 * - Contact + Storage batch permission for offline sync
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `contact sync flow - CONTACTS required and granted on first request`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var syncStarted = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CONTACTS, scope = testScope)

        handler.request { syncStarted = true }
        advanceUntilIdle()

        assertTrue(syncStarted, "Contact sync should start after permission granted")
    }

    @Test
    fun `contact picker flow - READ_CONTACTS sufficient and does not over-request CONTACTS`() = testScope.runTest {
        manager.configure(AppGrant.READ_CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        var pickerOpened = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.READ_CONTACTS, scope = testScope)

        handler.request { pickerOpened = true }
        advanceUntilIdle()

        assertTrue(pickerOpened)
        assertEquals(listOf<Any>(AppGrant.READ_CONTACTS), manager.requestedGrants,
            "Contact picker must only request READ_CONTACTS")
    }

    @Test
    fun `contact sync blocked - CONTACTS DENIED_ALWAYS requires Settings`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)

        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CONTACTS, scope = testScope)

        handler.request(settingsMessage = "Please enable Contacts in Settings to sync") {}
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showSettingsGuide, "Settings guide must appear when contacts permanently denied")
    }

    @Test
    fun `contact + storage batch - both permissions requested atomically`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.STORAGE, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CONTACTS, AppGrant.STORAGE),
            scope = testScope
        )

        groupHandler.request {}
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.CONTACTS])
        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.STORAGE])
    }

    @Test
    fun `contact sync recovery - DENIED then user grants in second attempt`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CONTACTS, scope = testScope)

        handler.request {}
        advanceUntilIdle()

        // Second attempt — user grants permission
        manager.configure(AppGrant.CONTACTS, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var syncStarted = false
        handler.request { syncStarted = true }
        advanceUntilIdle()

        assertTrue(syncStarted, "Contact sync should succeed on retry after grant")
    }

    @Test
    fun `requestSuspend CONTACTS returns status directly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CONTACTS, scope = testScope)

        val result = handler.requestSuspend()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, result)
    }
}
