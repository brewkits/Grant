package dev.brewkits.grant.motion

/**
 * Entry point for initializing the Grant Motion module.
 */
public expect object GrantMotion {
    /**
     * Initializes the Motion permission handler.
     * Must be called before requesting Motion permissions.
     * It is safe to call this multiple times.
     */
    public fun initialize()
}
