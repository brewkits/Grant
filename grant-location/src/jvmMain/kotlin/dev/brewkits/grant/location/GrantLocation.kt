package dev.brewkits.grant.location

/** See the common declaration. */
public actual object GrantLocation {
    /** No-op on the JVM: `grant-core` handles these permissions directly there. */
    public actual fun initialize() {
        // No-op
    }
}
