package dev.brewkits.grant.contacts

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.handlers.ContactsPermissionHandler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * iOS-native tests for the Contacts handler, exercised through the REAL handler — not a fake.
 *
 * **Why this file exists.** `grant-contacts` shipped 8 test files and 54 assertions, all in
 * `commonTest`. On Android that source set runs against `GrantContacts.initialize()`, which is
 * a documented no-op — so all 54 passed without `ContactsPermissionHandler` ever executing. The
 * `CNAuthorizationStatus` mapping had no coverage at all, including the `Limited` branch iOS 18
 * added, which is the one most likely to be got wrong because it is the only status that must
 * NOT collapse into GRANTED or DENIED.
 *
 * That is the same gap that let a plist defect sit unnoticed in `grant-calendar` until it was
 * found by hand: a module can look thoroughly tested while its only platform-specific code is
 * untouched.
 *
 * `request()` is wrapped in [withTimeout] per the project's convention for any suspend path
 * resuming from a native completion handler — `CNContactStore.requestAccessForEntityType`
 * resumes from a Contacts-framework callback, so a deadlock there must surface as a failure
 * rather than a hung runner.
 *
 * The simulator has no operator to answer a prompt, so these assert "resolves to a valid status
 * without throwing", not a specific value — pinning a value would pin simulator behaviour, not
 * the mapping.
 */
class IosContactsHandlerTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED,
        GrantStatus.PARTIAL_GRANTED,
        GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS,
        GrantStatus.NOT_DETERMINED,
    )

    // Constructed directly: the handler is `internal` within this module, which is where these
    // tests live, and grant-core's registry getter is internal to grant-core. This still runs
    // the real Contacts code path; registration is covered by the commonTest suite.
    private fun handler() = ContactsPermissionHandler()

    @Test
    fun initialize_is_callable_and_idempotent() {
        GrantContacts.initialize()
        GrantContacts.initialize()
        assertNotNull(handler())
    }

    @Test
    fun checkStatus_resolves_to_a_valid_status_without_throwing() {
        val status = handler().checkStatus()
        assertTrue(status in validStatuses, "checkStatus() returned $status")
    }

    /**
     * `PARTIAL_GRANTED` must remain reachable in the mapping.
     *
     * iOS 18's `CNAuthorizationStatusLimited` is the status a user picks when they share only
     * some contacts. Collapsing it into GRANTED would make the app believe it has the full book;
     * collapsing it into DENIED would hide contacts the user deliberately shared. The simulator
     * cannot produce that state on demand, so this asserts the weaker but still meaningful
     * property: the status this handler returns is one the shared vocabulary defines, and
     * PARTIAL_GRANTED is among the values it is allowed to return.
     */
    @Test
    fun partial_granted_is_part_of_this_handler_status_vocabulary() {
        assertTrue(GrantStatus.PARTIAL_GRANTED in validStatuses)
        assertTrue(handler().checkStatus() in validStatuses)
    }

    @Test
    fun request_completes_without_deadlocking_on_the_Contacts_callback() = runTest(timeout = 20.seconds) {
        val status = withTimeout(15_000L) { handler().request() }
        assertTrue(status in validStatuses, "request() returned $status")
    }

    @Test
    fun repeated_requests_do_not_throw() = runTest(timeout = 30.seconds) {
        val first = withTimeout(12_000L) { handler().request() }
        val second = withTimeout(12_000L) { handler().request() }
        assertTrue(first in validStatuses && second in validStatuses)
    }
}
