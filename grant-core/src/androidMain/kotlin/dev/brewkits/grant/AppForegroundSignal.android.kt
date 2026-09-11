package dev.brewkits.grant

/**
 * Not implemented on Android. See the KDoc on the `expect` declaration and on
 * [GrantHandler.autoRefreshOnForeground] for why this platform is lower-priority than iOS, and
 * for the narrow case ("granted, not revoked, from Settings while the app stayed alive") it
 * still leaves open here.
 */
internal actual object AppForegroundSignal {
    actual val isSupported: Boolean = false

    actual fun addListener(listener: () -> Unit): Any = Unit

    actual fun removeListener(token: Any) {}
}
