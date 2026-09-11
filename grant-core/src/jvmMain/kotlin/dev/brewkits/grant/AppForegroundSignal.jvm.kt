package dev.brewkits.grant

/**
 * Not implemented on the JVM target. A plain JVM process has no universal "app became active"
 * signal without assuming a windowing toolkit, and `grant-core` deliberately links none (see
 * `grant-desktop`'s own module-isolation notes). Consumers on desktop should call
 * [GrantHandler.refreshStatus] themselves from whatever window-focus hook their UI toolkit
 * offers.
 */
internal actual object AppForegroundSignal {
    actual val isSupported: Boolean = false

    actual fun addListener(listener: () -> Unit): Any = Unit

    actual fun removeListener(token: Any) {}
}
