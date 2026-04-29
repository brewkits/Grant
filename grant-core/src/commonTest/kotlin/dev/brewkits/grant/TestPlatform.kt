package dev.brewkits.grant

/**
 * Cross-platform helper for test-time platform detection.
 *
 * Used to skip or adapt assertions for platform-specific behavior.
 * For example, iOS does not support soft DENIED rationale flows,
 * so rationale-specific tests should guard with `assumeRationaleSupported()`.
 */
expect val isRationaleSupported: Boolean

/**
 * Skips a test on platforms where rationale dialogs are not supported (e.g. iOS).
 *
 * Usage:
 * ```kotlin
 * fun `request when DENIED should show rationale`() = runTest {
 *     assumeRationaleSupported { // no-ops on Android, returns early on iOS
 *         ...
 *     }
 * }
 * ```
 */
suspend fun assumeRationaleSupported(block: suspend () -> Unit) {
    if (isRationaleSupported) {
        block()
    }
    // else: silently skip on iOS
}
