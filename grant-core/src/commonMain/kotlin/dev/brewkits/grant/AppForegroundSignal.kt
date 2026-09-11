package dev.brewkits.grant

/**
 * Platform hook for "the app just returned to the foreground" — the signal
 * [GrantHandler.autoRefreshOnForeground] subscribes to.
 *
 * Real only on iOS today (`UIApplicationDidBecomeActiveNotification`): iOS can change photo,
 * location, and other permission grants from Settings while the app stays alive in the
 * background, so a `GrantHandler` constructed before that trip can go stale with no signal to
 * catch it. Android, `jvm`, and `js`/`wasmJs` are honest no-op stubs — [isSupported] is `false`
 * there, [addListener] never fires, and [GrantHandler.autoRefreshOnForeground] logs once rather
 * than silently pretending to listen. They are not implemented because the highest-value
 * documented gap this closes (Settings changing a grant behind a live process) is narrower on
 * those platforms: Android already restarts the process on most permission revocations, and the
 * built-in `GrantDialog`/`onReturnFromSettings()` flow already covers returning from Grant's own
 * Settings deep link — see the KDoc on [GrantHandler.autoRefreshOnForeground] for the case this
 * still leaves open there.
 */
internal expect object AppForegroundSignal {
    /** Whether [addListener] subscribes to a real OS signal on this platform. */
    val isSupported: Boolean

    /** Registers [listener] to run on every foreground transition; returns a removal token. */
    fun addListener(listener: () -> Unit): Any

    /** Idempotent — safe to call with a token that was already removed. */
    fun removeListener(token: Any)
}
