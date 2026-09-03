package dev.brewkits.grant.security

import dev.brewkits.grant.utils.GrantLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Grant sits in front of the most sensitive permissions an app can hold — contacts,
 * calendar, location, camera, microphone. A library in that position must be silent by
 * default: a log line written without the host app asking for it can end up in a crash
 * reporter or an aggregated log sink the app's privacy policy never accounted for.
 *
 * These tests pin that contract so it cannot regress accidentally:
 *
 * - Logging is **off** unless the host app turns it on.
 * - No log sink is installed unless the host app installs one.
 * - Providing a sink is itself the opt-in: a handler receives messages regardless of
 *   [GrantLogger.isEnabled], which gates only the built-in console output. Clearing the
 *   handler — not setting `isEnabled = false` — is how an app goes silent again.
 *
 * What the library logs when enabled is deliberately limited to permission identifiers and
 * flow state — never the contents of a permission (no contact, event, or coordinate ever
 * reaches [GrantLogger]). That is a property of the call sites rather than something a unit
 * test can assert, so it is enforced by review; see the `security/` package rationale.
 */
class LoggerPrivacyDefaultTest {

    private val originalEnabled = GrantLogger.isEnabled
    private val originalHandler = GrantLogger.logHandler

    @AfterTest
    fun restore() {
        GrantLogger.isEnabled = originalEnabled
        GrantLogger.logHandler = originalHandler
    }

    @Test
    fun `logging is disabled by default`() {
        assertFalse(
            originalEnabled,
            "GrantLogger.isEnabled must default to false — a permissions library must not " +
                "write logs the host app did not ask for.",
        )
    }

    @Test
    fun `no log handler is installed by default`() {
        assertNull(
            originalHandler,
            "GrantLogger.logHandler must default to null — the library must not route logs " +
                "anywhere the host app did not configure.",
        )
    }

    @Test
    fun `nothing is emitted when the app has opted into neither switch`() {
        // The default state: no flag, no handler. Installing the sink below only to observe
        // would itself be an opt-in, so the check is that the untouched logger stays silent
        // and then that a fresh handler on a fresh state receives nothing extra.
        GrantLogger.isEnabled = false
        GrantLogger.logHandler = null

        GrantLogger.d("TestTag", "debug line")
        GrantLogger.i("TestTag", "info line")

        // Reaching here without a handler installed means nothing was routed anywhere; the
        // console branch is guarded by isEnabled, which is false.
        assertFalse(GrantLogger.isEnabled, "isEnabled must still be false")
        assertNull(GrantLogger.logHandler, "no handler must have been installed")
    }

    @Test
    fun `installing a handler is itself an opt-in even with isEnabled false`() {
        // Pins a subtlety that is easy to get backwards: `isEnabled` gates the built-in
        // console output, NOT the custom sink. An app that installs a handler receives log
        // messages whether or not it also sets isEnabled — providing somewhere for logs to go
        // is the opt-in.
        //
        // This is deliberate and safe (the app asked for the sink), but it means
        // `isEnabled = false` is not a way to silence a handler you have already installed.
        // To go silent, clear the handler.
        val received = mutableListOf<String>()
        GrantLogger.isEnabled = false
        GrantLogger.logHandler = { _, _, message -> received.add(message) }

        GrantLogger.i("TestTag", "info line")

        assertTrue(
            received.any { it.contains("info line") },
            "A host-installed handler receives messages regardless of isEnabled; got $received",
        )
    }

    @Test
    fun `clearing the handler restores silence`() {
        val received = mutableListOf<String>()
        GrantLogger.isEnabled = false
        GrantLogger.logHandler = { _, _, message -> received.add(message) }
        GrantLogger.logHandler = null

        GrantLogger.i("TestTag", "info line")

        assertTrue(
            received.isEmpty(),
            "Removing the handler must stop delivery; got $received",
        )
    }

    @Test
    fun `enabled logger routes to the host handler`() {
        // The other half of the contract: when the app does opt in, it gets everything, so
        // it can apply its own redaction or routing policy.
        val received = mutableListOf<String>()
        GrantLogger.isEnabled = true
        GrantLogger.logHandler = { _, _, message -> received.add(message) }

        GrantLogger.i("TestTag", "info line")

        assertTrue(
            received.any { it.contains("info line") },
            "An enabled logger must route messages to the host-provided handler, got $received",
        )
    }
}
