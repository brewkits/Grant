package dev.brewkits.grant.handlers

import dev.brewkits.grant.utils.GrantLogger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for a false-alarm log line found via manual device testing (2026-09-03):
 * the iOS Simulator demo app's `Info.plist` carries only the legacy `NSCalendarsUsageDescription`
 * key — a **correct**, sufficient configuration — yet the console showed
 * `❌ [CalendarPermissionHandler] MISSING Info.plist key: 'NSCalendarsFullAccessUsageDescription'
 * ... Returning DENIED_ALWAYS as a safety fallback`, even though the actual result was
 * `NOT_DETERMINED`, never `DENIED_ALWAYS`.
 *
 * Cause: the plist check accepts *either* key (legacy or the iOS 17+ full-access key), but it
 * was built out of two calls to [dev.brewkits.grant.utils.hasInfoPlistKey] — a shared helper
 * that unconditionally logs an error for the *one* key it was asked about. Checking two keys
 * with OR therefore logged a false "returning DENIED_ALWAYS" alarm for whichever key happened
 * to be absent, even when the other key present made the real result something else entirely.
 *
 * Drives [evaluateCalendarPlistKeys] — the decision logic pulled out of the `NSBundle` lookup —
 * directly, through all four (hasLegacy, hasFull) combinations. An earlier version of this test
 * called through [CalendarPermissionHandler.checkStatus] and inferred ground truth from the test
 * bundle's real `Info.plist`; that bundle happens to carry *neither* calendar key, so both the
 * buggy and the fixed implementation logged in exactly the same case and the test could not have
 * told them apart. Testing the pure decision function directly closes that gap.
 */
class CalendarPlistLoggingTest {

    private val originalEnabled = GrantLogger.isEnabled
    private val originalHandler = GrantLogger.logHandler
    private val messages = mutableListOf<String>()

    @BeforeTest
    fun setup() {
        messages.clear()
        GrantLogger.isEnabled = false
        GrantLogger.logHandler = { _, tag, message ->
            if (tag == "CalendarPermissionHandler") messages.add(message)
        }
    }

    @AfterTest
    fun restore() {
        GrantLogger.isEnabled = originalEnabled
        GrantLogger.logHandler = originalHandler
    }

    /**
     * iOS 17 split calendar access into full and write-only, and an app that only *adds*
     * events is encouraged to declare just `NSCalendarsWriteOnlyAccessUsageDescription`.
     * Before this key was accepted, that correctly-configured app was reported DENIED_ALWAYS
     * before EventKit was ever consulted. Confirmed against the iOS 26.5 runtime, which
     * defines all three calendar keys.
     */
    @Test
    fun `write-only key alone is sufficient and does not log MISSING`() {
        val result = evaluateCalendarPlistKeys(hasLegacy = false, hasFull = false, hasWriteOnly = true)
        assertTrue(result, "the iOS 17+ write-only key alone must be sufficient")
        assertTrue(messages.isEmpty(), "must not log when the write-only key covers it, got $messages")
    }

    @Test
    fun `no key at all still logs exactly one accurate MISSING naming all three keys`() {
        val result = evaluateCalendarPlistKeys(hasLegacy = false, hasFull = false, hasWriteOnly = false)
        assertEquals(false, result, "no calendar key means the app is genuinely misconfigured")
        assertEquals(1, messages.size, "exactly one log line for the one real failure, got $messages")
        val message = messages.single()
        assertTrue(
            message.contains("NSCalendarsWriteOnlyAccessUsageDescription"),
            "the guidance must name the write-only key too, or a developer told to add a key " +
                "may add a wider-scope one than they need; got: $message",
        )
    }

    @Test
    fun `legacy key only does not log MISSING`() {
        val result = evaluateCalendarPlistKeys(hasLegacy = true, hasFull = false)
        assertTrue(result, "legacy key alone must be sufficient")
        assertTrue(messages.isEmpty(), "must not log when the legacy key covers it, got $messages")
    }

    @Test
    fun `full access key only does not log MISSING`() {
        val result = evaluateCalendarPlistKeys(hasLegacy = false, hasFull = true)
        assertTrue(result, "full-access key alone must be sufficient")
        assertTrue(messages.isEmpty(), "must not log when the full-access key covers it, got $messages")
    }

    @Test
    fun `both keys present does not log MISSING`() {
        val result = evaluateCalendarPlistKeys(hasLegacy = true, hasFull = true)
        assertTrue(result)
        assertTrue(messages.isEmpty(), "must not log when both keys are present, got $messages")
    }

    @Test
    fun `neither key present logs exactly one accurate MISSING error`() {
        val result = evaluateCalendarPlistKeys(hasLegacy = false, hasFull = false)
        assertEquals(false, result)
        assertEquals(1, messages.size, "expected exactly one log line, got $messages")
        assertTrue(
            messages.single().contains("MISSING Info.plist key"),
            "message should describe the missing key: ${messages.single()}",
        )
    }
}
