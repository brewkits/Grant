package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Pins the "honest no-op" contract from [AppForegroundSignal]'s KDoc: Android has no real
 * foreground signal wired up, so [AppForegroundSignal.isSupported] must stay `false` and
 * [AppForegroundSignal.addListener]/[AppForegroundSignal.removeListener] must be safely inert
 * rather than silently pretending to subscribe.
 */
class AppForegroundSignalAndroidTest {

    @Test
    fun `isSupported is false on Android`() {
        assertFalse(AppForegroundSignal.isSupported)
    }

    @Test
    fun `addListener and removeListener never invoke the listener and are safe to call twice`() {
        var callCount = 0
        val token = AppForegroundSignal.addListener { callCount++ }

        AppForegroundSignal.removeListener(token)
        AppForegroundSignal.removeListener(token) // must not throw

        assertFalse(callCount > 0, "the no-op signal must never invoke its listener")
    }
}
