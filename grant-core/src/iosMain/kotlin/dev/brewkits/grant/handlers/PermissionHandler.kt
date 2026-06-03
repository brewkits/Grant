package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus

/**
 * Common interface for all iOS permission handlers.
 *
 * Each handler encapsulates the native framework imports and logic
 * for a specific permission group, preventing unused frameworks
 * from being statically linked into apps that don't need them.
 *
 * See [APPLE_FRAMEWORK_LINKING_ISSUE.md](docs/ios/APPLE_FRAMEWORK_LINKING_ISSUE.md)
 * for the architectural rationale behind this design.
 */
interface PermissionHandler {
    /**
     * Returns the current authorization status for this permission.
     * Must be called on the main thread.
     */
    fun checkStatus(): GrantStatus

    /**
     * Requests authorization from the user.
     * Suspends until the system dialog is dismissed.
     */
    suspend fun request(): GrantStatus
}
