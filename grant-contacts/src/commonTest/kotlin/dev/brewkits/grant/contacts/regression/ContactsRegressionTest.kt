package dev.brewkits.grant.contacts.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.contacts.GrantContacts
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression tests for v2.0 module split and Contacts-specific edge cases.
 *
 * Documents behavior that must NOT regress:
 * - v2.0: CONTACTS and READ_CONTACTS are independent identifiers
 * - v2.0: GrantContacts.initialize() is safe to call multiple times (idempotent)
 * - GrantHandler correctly propagates request to the configured GrantManager
 * - DENIED state does not permanently block subsequent requests
 * - CONTACTS and READ_CONTACTS handlers share no implicit state
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── v2.0 module split regressions ─────────────────────────────────────

    @Test
    fun `v2_0 - GrantContacts initialize is idempotent across test runs`() {
        // Calling initialize() multiple times must never throw or corrupt state.
        GrantContacts.initialize()
        GrantContacts.initialize()
        GrantContacts.initialize()
        // No assertion needed — absence of exception is the contract.
    }

    @Test
    fun `v2_0 - CONTACTS and READ_CONTACTS have distinct permission identifiers`() {
        assertNotEquals(
            AppGrant.CONTACTS.identifier,
            AppGrant.READ_CONTACTS.identifier,
            "CONTACTS and READ_CONTACTS must be distinct identifiers for independent tracking"
        )
    }

    @Test
    fun `v2_0 - CONTACTS handler does not request READ_CONTACTS and vice versa`() = testScope.runTest {
        manager.mockRequestResult = GrantStatus.GRANTED
        manager.mockStatus = GrantStatus.NOT_DETERMINED

        val contactsHandler = GrantHandler(manager, AppGrant.CONTACTS, testScope)
        contactsHandler.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.CONTACTS, manager.requestedGrants[0],
            "CONTACTS handler must only request AppGrant.CONTACTS")

        manager.reset()

        val readContactsHandler = GrantHandler(manager, AppGrant.READ_CONTACTS, testScope)
        readContactsHandler.request {}
        advanceUntilIdle()

        assertEquals(1, manager.requestedGrants.size)
        assertEquals(AppGrant.READ_CONTACTS, manager.requestedGrants[0],
            "READ_CONTACTS handler must only request AppGrant.READ_CONTACTS")
    }

    // ── state machine regressions ──────────────────────────────────────────

    @Test
    fun `DENIED state does not permanently block subsequent request`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CONTACTS, testScope)

        handler.request {}
        advanceUntilIdle()

        // Simulate user granting permission manually (e.g., in Settings)
        manager.configure(AppGrant.CONTACTS, GrantStatus.GRANTED, GrantStatus.GRANTED)
        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "DENIED state must not permanently block future requests")
    }

    @Test
    fun `onDismiss after DENIED clears dialog state without requesting again`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CONTACTS, testScope)

        handler.request {}
        advanceUntilIdle()
        val requestCountAfterFirst = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        val state = handler.state.value
        assertFalse(state.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(requestCountAfterFirst, manager.requestedGrants.size,
            "Dismiss must not trigger another request")
    }

    @Test
    fun `CONTACTS and READ_CONTACTS state machines are completely independent`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        manager.configure(AppGrant.READ_CONTACTS, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val contactsHandler = GrantHandler(grantManager = manager, grant = AppGrant.CONTACTS, scope = testScope)
        val readContactsHandler = GrantHandler(grantManager = manager, grant = AppGrant.READ_CONTACTS, scope = testScope)

        contactsHandler.request(settingsMessage = "Enable in Settings") {}
        var readGranted = false
        readContactsHandler.request { readGranted = true }
        advanceUntilIdle()

        assertTrue(contactsHandler.state.value.showSettingsGuide,
            "CONTACTS handler should show settings guide (DENIED_ALWAYS)")
        assertTrue(readGranted,
            "READ_CONTACTS handler should succeed independently of CONTACTS state")
    }

    @Test
    fun `refreshStatus after GRANTED stays GRANTED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, testScope)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)

        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
