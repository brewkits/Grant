package dev.brewkits.grant.contacts

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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GrantContactsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    // ── Initialization ──────────────────────────────────────────────────────

    @Test
    fun initialize_doesNotThrow() {
        GrantContacts.initialize()
    }

    @Test
    fun initialize_isIdempotent() {
        GrantContacts.initialize()
        GrantContacts.initialize()
        GrantContacts.initialize()
    }

    // ── AppGrant identifiers ───────────────────────────────────────────────

    @Test
    fun `CONTACTS identifier is non-empty`() {
        assertTrue(AppGrant.CONTACTS.identifier.isNotEmpty())
    }

    @Test
    fun `READ_CONTACTS identifier is non-empty`() {
        assertTrue(AppGrant.READ_CONTACTS.identifier.isNotEmpty())
    }

    @Test
    fun `CONTACTS and READ_CONTACTS have distinct identifiers`() {
        assertNotEquals(AppGrant.CONTACTS.identifier, AppGrant.READ_CONTACTS.identifier)
    }

    @Test
    fun `CONTACTS requiresBackgroundUpgrade is false`() {
        assertFalse(AppGrant.CONTACTS.requiresBackgroundUpgrade)
    }

    @Test
    fun `READ_CONTACTS requiresBackgroundUpgrade is false`() {
        assertFalse(AppGrant.READ_CONTACTS.requiresBackgroundUpgrade)
    }

    // ── GrantHandler basics ────────────────────────────────────────────────

    @Test
    fun `CONTACTS handler initial status is NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `READ_CONTACTS handler initial status is NOT_DETERMINED`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.READ_CONTACTS, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.NOT_DETERMINED, handler.status.value)
    }

    @Test
    fun `request CONTACTS when GRANTED invokes callback without re-requesting`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        var called = false
        handler.request { called = true }
        advanceUntilIdle()

        assertTrue(called)
        assertFalse(manager.requestCalled, "Already GRANTED — no system dialog should be triggered")
    }

    @Test
    fun `request READ_CONTACTS DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.READ_CONTACTS, this)

        handler.request(settingsMessage = "Enable Contacts in Settings") {}
        advanceUntilIdle()

        assertTrue(handler.state.value.showSettingsGuide)
    }

    @Test
    fun `refreshStatus updates status from manager`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)
        handler.refreshStatus()
        advanceUntilIdle()
        assertEquals(GrantStatus.DENIED, handler.status.value)
    }

    @Test
    fun `onDismiss hides dialog without triggering new request`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED, GrantStatus.DENIED)
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        handler.request {}
        advanceUntilIdle()
        val countBefore = manager.requestedGrants.size

        handler.onDismiss()
        advanceUntilIdle()

        assertFalse(handler.state.value.isVisible, "Dialog must be hidden after dismiss")
        assertEquals(countBefore, manager.requestedGrants.size, "Dismiss must not trigger another request")
    }
}
