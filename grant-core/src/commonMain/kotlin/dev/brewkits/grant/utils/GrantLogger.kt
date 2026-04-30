package dev.brewkits.grant.utils

/**
 * A lightweight logging utility for the Grant library.
 *
 * Use this to monitor permission flows, debug platform-specific denials, or
 * integrate Grant's internal logs with your app's logging framework.
 *
 * ### Usage Example
 * ```kotlin
 * GrantLogger.isEnabled = true
 * GrantLogger.logHandler = { level, tag, message ->
 *     MyAnalytics.log("[$tag] $message")
 * }
 * ```
 */
object GrantLogger {
    /**
     * Globally enables or disables internal library logging.
     *
     * Defaults to `false`.
     */
    var isEnabled: Boolean = false

    /**
     * A custom lambda for redirecting library logs.
     *
     * If provided, this handler takes precedence over the default console output.
     */
    var logHandler: ((level: LogLevel, tag: String, message: String) -> Unit)? = null

    /** Logs a debug message. */
    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    /** Logs an informational message. */
    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    /** Logs a warning message. */
    fun w(tag: String, message: String) {
        log(LogLevel.WARNING, tag, message)
    }

    /** Logs an error message with an optional exception. */
    fun e(tag: String, message: String, error: Throwable? = null) {
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
    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
