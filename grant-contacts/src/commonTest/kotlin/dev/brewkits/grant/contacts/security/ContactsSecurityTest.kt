package dev.brewkits.grant.contacts.security

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security tests for the Contacts permission module.
 *
 * - SEC-C01: Callback cleared on dismiss (no memory leak)
 * - SEC-C02: CONTACTS and READ_CONTACTS status are strictly isolated
 * - SEC-C03: Concurrent handlers cannot corrupt each other's callbacks
 * - SEC-C04: Multiple initialize() calls are idempotent (no double-registration)
 * - SEC-C05: Re-entrancy — requesting while DENIED_ALWAYS never loops
 * - SEC-C06: DENIED_ALWAYS cannot be bypassed by repeated request() calls
 * - SEC-C07: Callback invoked at most once per request cycle
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsSecurityTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `SEC-C01 - callback cleared on dismiss and not invoked after late grant`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        var invoked = false
        handler.request(settingsMessage = "Enable Contacts") { invoked = true }
        advanceUntilIdle()

        handler.onDismiss()
        advanceUntilIdle()

        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()

        assertFalse(invoked, "Callback dismissed before grant — must not fire retroactively")
    }

    @Test
    fun `SEC-C02 - CONTACTS and READ_CONTACTS status are fully isolated`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED_ALWAYS)
        manager.configure(AppGrant.READ_CONTACTS, GrantStatus.GRANTED)

        val contactsHandler = GrantHandler(manager, AppGrant.CONTACTS, this)
        val readContactsHandler = GrantHandler(manager, AppGrant.READ_CONTACTS, this)

        contactsHandler.refreshStatus()
        readContactsHandler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.DENIED_ALWAYS, contactsHandler.status.value,
            "CONTACTS status must not be affected by READ_CONTACTS state")
        assertEquals(GrantStatus.GRANTED, readContactsHandler.status.value,
            "READ_CONTACTS status must not be affected by CONTACTS state")
    }

    @Test
    fun `SEC-C03 - concurrent handlers do not corrupt each other's callbacks`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        var contactsCallbackCount = 0
        var readContactsCallbackCount = 0

        val h1 = GrantHandler(manager, AppGrant.CONTACTS, this)
        val h2 = GrantHandler(manager, AppGrant.READ_CONTACTS, this)

        repeat(10) {
            h1.request { contactsCallbackCount++ }
            h2.request { readContactsCallbackCount++ }
        }
        advanceUntilIdle()

        assertTrue(contactsCallbackCount >= 1, "CONTACTS handler must fire at least once")
        assertTrue(readContactsCallbackCount >= 1, "READ_CONTACTS handler must fire at least once")
    }

    @Test
    fun `SEC-C04 - multiple initialize calls do not double-register or corrupt state`() = testScope.runTest {
        repeat(10) { GrantContacts.initialize() }

        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        var grantedCalled = false
        handler.request { grantedCalled = true }
        advanceUntilIdle()

        assertTrue(grantedCalled, "Handler must work normally after repeated initialize() calls")
    }

    @Test
    fun `SEC-C05 - DENIED_ALWAYS does not trigger infinite request loop`() = testScope.runTest {
        manager.configure(AppGrant.CONTACTS, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        repeat(5) {
            handler.request(settingsMessage = "Enable Contacts in Settings") {}
            advanceUntilIdle()
        }

        val requestCount = manager.requestedGrants.count { it == AppGrant.CONTACTS }
        assertEquals(0, requestCount, "DENIED_ALWAYS must never trigger a system dialog request")
    }

    @Test
    fun `SEC-C06 - repeated requests after DENIED_ALWAYS do not bypass block`() = testScope.runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        var grantedCalled = false
        repeat(20) {
            handler.request(settingsMessage = "Enable") { grantedCalled = true }
            advanceUntilIdle()
        }

        assertFalse(grantedCalled, "DENIED_ALWAYS must never invoke the granted callback")
        assertEquals(GrantStatus.DENIED_ALWAYS, handler.status.value)
    }

    @Test
    fun `SEC-C07 - onGranted callback fires at most once per request cycle`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        var callCount = 0
        handler.request { callCount++ }
        advanceUntilIdle()

        assertEquals(1, callCount, "onGranted must fire exactly once per request when already GRANTED")
    }

    @Test
    fun `SEC-C08 - status remains consistent across refreshStatus calls`() = testScope.runTest {
        manager.mockStatus = GrantStatus.GRANTED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        repeat(20) {
            handler.refreshStatus()
            advanceUntilIdle()
            assertEquals(GrantStatus.GRANTED, handler.status.value,
                "Status must remain stable across repeated refresh calls")
        }
    }

    @Test
    fun `SEC-C09 - handler state value is never null`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        val handler = GrantHandler(manager, AppGrant.CONTACTS, this)

        assertNotNull(handler.state.value, "State must never be null")
        assertNotNull(handler.status.value, "Status must never be null")

        handler.refreshStatus()
        advanceUntilIdle()

        assertNotNull(handler.state.value)
        assertNotNull(handler.status.value)
    }
}
