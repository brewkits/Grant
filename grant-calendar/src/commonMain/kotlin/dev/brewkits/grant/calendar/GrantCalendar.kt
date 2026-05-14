package dev.brewkits.grant.calendar

/**
 * Entry point for initializing the Grant Calendar module.
 */
expect object GrantCalendar {
    /**
     * Initializes the Calendar permission handler.
     * Must be called before requesting Calendar permissions.
     * It is safe to call this multiple times.
     */
    fun initialize()
}
