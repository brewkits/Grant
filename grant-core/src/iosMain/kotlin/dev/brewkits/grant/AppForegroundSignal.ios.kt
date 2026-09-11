package dev.brewkits.grant

import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification

/**
 * `UIApplicationDidBecomeActiveNotification` fires every time the app returns to the
 * foreground, including a return from the Settings app — exactly the transition
 * [GrantHandler.autoRefreshOnForeground] needs. The observer token `addObserverForName`
 * returns is opaque on the Kotlin side but is what `removeObserver` expects back, so it is
 * passed through as this function's `Any` token rather than wrapped.
 *
 * `queue = null` rather than `NSOperationQueue.mainQueue` deliberately: with a queue, the
 * block runs *asynchronously* even when posted from the main thread (Apple's own documented
 * behavior), which UIKit's own posting of this notification already happens on the main
 * thread anyway — so `null` runs the listener synchronously on the same (main) thread the
 * notification was posted from, with no run-loop turn to wait out.
 */
internal actual object AppForegroundSignal {
    actual val isSupported: Boolean = true

    actual fun addListener(listener: () -> Unit): Any =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { _ -> listener() }

    actual fun removeListener(token: Any) {
        NSNotificationCenter.defaultCenter.removeObserver(token)
    }
}
