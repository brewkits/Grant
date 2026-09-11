package dev.brewkits.grant

/**
 * Not implemented in the browser. A backgrounded tab does not lose or regain OS-level
 * permission grants the way a native app can via a Settings round trip — `navigator.permissions`
 * is cheap to re-query directly, so there is no equivalent gap here to close. Consumers who want
 * this can call [GrantHandler.refreshStatus] from a `visibilitychange` listener themselves.
 */
internal actual object AppForegroundSignal {
    actual val isSupported: Boolean = false

    actual fun addListener(listener: () -> Unit): Any = Unit

    actual fun removeListener(token: Any) {}
}
