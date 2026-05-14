package dev.brewkits.grant.contacts.integration

import app.cash.turbine.test
import dev.brewkits.grant.AppGrant
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for CONTACTS and READ_CONTACTS permissions through GrantHandler.
 *
 * Verifies the complete state machine: NOT_DETERMINED → request → result,
 * blocked paths (DENIED_ALWAYS), and independence of CONTACTS vs READ_CONTACTS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrantContactsIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── checkStatus ────────────────────────────────────────────────────────

    @Test
    fun `checkStatus CONTACTS returns valid GrantStatus`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = contactsHandler()
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `checkStatus READ_CONTACTS returns valid GrantStatus independently`() = testScope.runTest {
        manager.configure(AppGrant.READ_CONTACTS, GrantStatus.GRANTED)
        manager.configure(AppGrant.CONTACTS, GrantStatus.NOT_DETERMINED)
        val readHandler = readContactsHandler()
        val fullHandler = contactsHandler()
        readHandler.refreshStatus()
        fullHandler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, readHandler.status.value)
        assertEquals(GrantStatus.NOT_DETERMINED, fullHandler.status.value)
    }

    // ── request happy path ─────────────────────────────────────────────────

    @Test
    fun `request CONTACTS NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = contactsHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertTrue(manager.requestCalled)
    }

    @Test
    fun `request READ_CONTACTS NOT_DETERMINED to GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = readContactsHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertEquals(AppGrant.READ_CONTACTS, manager.requestedGrants.first())
    }

    @Test
    fun `request CONTACTS when already GRANTED does not re-request`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = contactsHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()
        assertTrue(grantedCalled)
        assertFalse(manager.requestCalled, "Should not call request() when already GRANTED")
    }

    // ── blocked path ───────────────────────────────────────────────────────

    @Test
    fun `CONTACTS DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CONTACTS, scope = testScope)
        handler.request(settingsMessage = "Enable contacts in Settings") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide, "Settings guide should be visible for DENIED_ALWAYS")
            assertFalse(state.showRationale)
        }
    }

    @Test
    fun `READ_CONTACTS DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.READ_CONTACTS, scope = testScope)
        handler.request(settingsMessage = "Enable contacts in Settings") {}
        advanceUntilIdle()
        handler.state.test {
            val state = awaitItem()
            assertTrue(state.showSettingsGuide)
        }
    }

    // ── CONTACTS vs READ_CONTACTS independence ─────────────────────────────

    @Test
    fun `CONTACTS and READ_CONTACTS are independent permissions`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.READ_CONTACTS, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)

        val contactsHandler = contactsHandler()
        val readContactsHandler = readContactsHandler()

        var contactsGranted = false

        contactsHandler.request { contactsGranted = true }
        readContactsHandler.request(settingsMessage = "Enable in Settings") {}
        advanceUntilIdle()

        assertTrue(contactsGranted, "CONTACTS should be GRANTED")
        assertEquals(AppGrant.CONTACTS, manager.requestedGrants.first())
    }

    @Test
    fun `least privilege - READ_CONTACTS preferred over CONTACTS for read-only access`() = testScope.runTest {
        manager.configure(AppGrant.READ_CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val handler = readContactsHandler()
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled)
        assertEquals(AppGrant.READ_CONTACTS, manager.requestedGrants.first(),
            "Least-privilege request should use READ_CONTACTS, not CONTACTS")
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun contactsHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.CONTACTS,
        scope = testScope
    )

    private fun readContactsHandler() = GrantHandler(
        grantManager = manager,
        grant = AppGrant.READ_CONTACTS,
        scope = testScope
    )
}
