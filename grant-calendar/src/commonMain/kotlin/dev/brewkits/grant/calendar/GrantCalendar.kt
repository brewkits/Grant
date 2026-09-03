package dev.brewkits.grant.calendar

/**
 * Entry point for initializing the Grant Calendar module.
 */
public expect object GrantCalendar {
    /**
     * Initializes the Calendar permission handler.
     * Must be called before requesting Calendar permissions.
     * It is safe to call this multiple times.
     */
    public fun initialize()
}
