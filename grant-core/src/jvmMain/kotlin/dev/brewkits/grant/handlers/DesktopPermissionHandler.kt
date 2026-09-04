package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus

/**
 * Common interface for JVM desktop permission handlers (macOS TCC today; a future
 * Windows-equivalent, if one is ever added, would implement this too).
 *
 * Mirrors `PermissionHandler` (iOS) in shape, not by sharing the type: the iOS interface
 * documents a main-thread requirement that has no JVM equivalent, and iOS/desktop handlers
 * are registered into separate registries with separate opt-in modules, so keeping them
 * distinct types avoids conflating two unrelated platforms' constraints.
 */
public interface DesktopPermissionHandler {
    /** Returns the current authorization status for this permission. */
    public fun checkStatus(): GrantStatus

    /** Requests authorization from the user. Suspends until the system dialog is dismissed. */
    public suspend fun request(): GrantStatus
}
