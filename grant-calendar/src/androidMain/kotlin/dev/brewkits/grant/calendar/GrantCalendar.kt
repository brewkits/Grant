package dev.brewkits.grant.calendar

/**
 * Entry point for initializing the Grant Calendar module.
 */
public actual object GrantCalendar {
    /**
     * No-op on Android, as Android handles permissions via Manifest and Intents.
     */
    public actual fun initialize() {
        // No-op
    }
}
