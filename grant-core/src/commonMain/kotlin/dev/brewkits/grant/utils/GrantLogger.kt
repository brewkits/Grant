package dev.brewkits.grant.utils

/**
 * Simple logger interface for grant library.
 *
 * This allows apps to:
 * - Enable/disable logging
 * - Integrate with their own logging framework
 * - Control log output
 */
object GrantLogger {
    /**
     * Enable or disable logging.
     * Default: false (no logs in production)
     */
    var isEnabled: Boolean = false

    /**
     * Custom log handler.
     * Default: prints to console if enabled
     */
    var logHandler: ((level: LogLevel, tag: String, message: String) -> Unit)? = null

    /**
     * Log a debug message
     */
    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    /**
     * Log an info message
     */
    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    /**
     * Log a warning message
     */
    fun w(tag: String, message: String) {
        log(LogLevel.WARNING, tag, message)
    }

    /**
     * Log an error message
     */
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
            // Default console output
            val emoji = when (level) {
                LogLevel.DEBUG -> "🔍"
                LogLevel.INFO -> "ℹ️"
                LogLevel.WARNING -> "⚠️"
                LogLevel.ERROR -> "❌"
            }
            println("$emoji [$tag] $message")
        }
    }

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
