package dev.brewkits.grant.tracking

/**
 * Entry point for initializing the Grant Tracking (App Tracking Transparency) module.
 */
public expect object GrantTracking {
    /**
     * Initializes the App Tracking Transparency handler.
     * Must be called before requesting [dev.brewkits.grant.AppGrant.APP_TRACKING].
     * It is safe to call this multiple times.
     */
    public fun initialize()
}
