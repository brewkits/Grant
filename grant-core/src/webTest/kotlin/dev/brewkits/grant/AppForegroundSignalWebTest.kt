package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Pins the "honest no-op" contract from [AppForegroundSignal]'s KDoc: browsers have no
 * foreground signal wired up here, so [AppForegroundSignal.isSupported] must stay `false` and
 * [AppForegroundSignal.addListener]/[AppForegroundSignal.removeListener] must be safely inert
 * rather than silently pretending to subscribe. Runs against both `js` and `wasmJs` (shared
 * `webTest` source set), matching [dev.brewkits.grant.impl.WebGrantDelegateTest]'s coverage.
 */
class AppForegroundSignalWebTest {

    @Test
    fun `isSupported is false on the browser target`() {
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
