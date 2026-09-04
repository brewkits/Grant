package dev.brewkits.grant.utils

/**
 * A lightweight logging utility for the Grant library's internal diagnostics — missing
 * `Info.plist` keys, unregistered opt-in modules, Settings-navigation failures, and similar
 * edge cases the library itself detects.
 *
 * This is not a step-by-step audit trail of every permission flow (requested/granted/denied/
 * rationale-shown/settings-opened) — that is [dev.brewkits.grant.GrantEventListener], a
 * separate, purpose-built mechanism attached per [dev.brewkits.grant.GrantHandler]. The two
 * are complementary: this one for "something the library wants a developer to notice",
 * [dev.brewkits.grant.GrantEventListener] for "what happened in this permission flow".
 *
 * ### Android: the default console output needs `logHandler` on retail devices
 *
 * The built-in console output (enabled via [isEnabled] alone) is a plain Kotlin `println`.
 * On Android, `println`/`System.out` is routed to Logcat only on `userdebug`/`eng` system
 * images (emulators, most physical dev/test hardware) — a retail `user`-build device (the
 * OS type on essentially every phone a real user carries: `ro.build.type=user`,
 * `ro.debuggable=0`) never forwards it, silently. `isEnabled = true` with no [logHandler] is
 * therefore enough to see Grant's diagnostics on an emulator but **produces nothing visible**
 * on a real production device — verified against a physical Android 17 device in this
 * project's own test pass. To see logs there, install a [logHandler] that calls
 * `android.util.Log.d/w/e` (or a crash reporter / analytics SDK) instead of relying on the
 * console branch.
 *
 * ### Usage Example
 * ```kotlin
 * GrantLogger.isEnabled = true
 * GrantLogger.logHandler = { level, tag, message ->
 *     MyAnalytics.log("[$tag] $message")
 * }
 * ```
 */
public object GrantLogger {
    /**
     * Enables the built-in console output.
     *
     * Defaults to `false`: a library that sits in front of contacts, calendar and location
     * must not write logs the host app never asked for.
     *
     * This gates the **console output only** — it does not gate [logHandler]. See there.
     */
    public var isEnabled: Boolean = false

    /**
     * A custom sink for library logs.
     *
     * Installing a handler is **itself an opt-in**: it receives messages regardless of
     * [isEnabled], which gates only the built-in console output. Setting `isEnabled = false`
     * therefore does *not* silence a handler already installed — set this back to `null`
     * to do that.
     *
     * Only permission identifiers and flow state are passed here. The contents of a
     * permission — a contact, an event, a coordinate — never reach the logger.
     */
    public var logHandler: ((level: LogLevel, tag: String, message: String) -> Unit)? = null

    /** Logs a debug message. */
    public fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    /** Logs an informational message. */
    public fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    /** Logs a warning message. */
    public fun w(tag: String, message: String) {
        log(LogLevel.WARNING, tag, message)
    }

    /** Logs an error message with an optional exception. */
    public fun e(tag: String, message: String, error: Throwable? = null) {
        val fullMessage = if (error != null) {
            "$message: ${error.message}"
        } else {
            message
        }
        log(LogLevel.ERROR, tag, fullMessage)
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        if (!isEnabled && logHandler == null) return

        val handler = logHandler
        if (handler != null) {
            handler(level, tag, message)
        } else if (isEnabled) {
            val emoji = when (level) {
                LogLevel.DEBUG -> "🔍"
                LogLevel.INFO -> "ℹ️"
                LogLevel.WARNING -> "⚠️"
                LogLevel.ERROR -> "❌"
            }
            println("$emoji [$tag] $message")
        }
    }

    /** Log severity levels. */
    public enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
