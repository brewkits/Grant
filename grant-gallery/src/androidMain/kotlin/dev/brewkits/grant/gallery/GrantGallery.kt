package dev.brewkits.grant.gallery

/** See the common declaration. */
public actual object GrantGallery {
    /** No-op on Android: `grant-core` handles these permissions directly there. */
    public actual fun initialize() {
        // No-op
    }
}
