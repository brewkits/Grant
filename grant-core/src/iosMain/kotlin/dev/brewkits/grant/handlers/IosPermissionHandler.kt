package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus

/**
 * Common interface for all iOS permission handlers.
 *
 * Each handler encapsulates the native framework imports and logic
 * for a specific permission group, preventing unused frameworks
 * from being statically linked into apps that don't need them.
 *
 * **Note on naming (Issue #12):** The `Ios` prefix is technically redundant since this
 * interface lives in `iosMain` and is already platform-scoped. KMP convention prefers
 * plain names in platform source sets (e.g. `PermissionHandler`). The rename is deferred
 * to avoid a large churn across 8+ files with no public API impact.
 *
 * See [APPLE_FRAMEWORK_LINKING_ISSUE.md](docs/ios/APPLE_FRAMEWORK_LINKING_ISSUE.md)
 * for the architectural rationale behind this design.
 */
interface IosPermissionHandler {
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
