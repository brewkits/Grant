package dev.brewkits.grant.motion

/**
 * Entry point for initializing the Grant Motion module.
 */
expect object GrantMotion {
    /**
     * Initializes the Motion permission handler.
     * Must be called before requesting Motion permissions.
     * It is safe to call this multiple times.
     */
    fun initialize()
}
