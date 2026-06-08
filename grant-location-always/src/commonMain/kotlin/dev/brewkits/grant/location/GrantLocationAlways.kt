package dev.brewkits.grant.location

/**
 * Entry point for initializing the Grant LocationAlways module.
 */
expect object GrantLocationAlways {
    /**
     * Initializes the LocationAlways permission handler.
     * Must be called before requesting LocationAlways permissions.
     * It is safe to call this multiple times.
     */
    fun initialize()
}
