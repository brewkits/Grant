package dev.brewkits.grant.tracking

/**
 * Entry point for initializing the Grant Tracking module.
 */
public actual object GrantTracking {
    /**
     * No-op on Android. There is no runtime permission for cross-app tracking: the advertising
     * ID is gated by `com.google.android.gms.permission.AD_ID`, an install-time permission with
     * no dialog. `grant-core` already reports `APP_TRACKING` as GRANTED there without a prompt.
     *
     * Adding this module to a shared KMP source set is therefore safe — it simply does nothing
     * on Android, exactly like `grant-contacts` and the other iOS-isolation modules.
     */
    public actual fun initialize() {
        // No-op
    }
}
