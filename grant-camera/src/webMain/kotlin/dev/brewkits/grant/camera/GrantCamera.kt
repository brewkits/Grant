package dev.brewkits.grant.camera

/** See the common declaration. */
public actual object GrantCamera {
    /** No-op on the browser: `grant-core` handles these permissions directly there. */
    public actual fun initialize() {
        // No-op
    }
}
