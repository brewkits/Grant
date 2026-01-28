package dev.brewkits.grant

/**
 * Platform-agnostic interface for saving and restoring UI state across process death.
 *
 * **Why This Exists:**
 * - Android can kill processes at any time to reclaim memory
 * - Dialog state (GrantHandler UI state) is lost on process death
 * - SavedStateHandle is Android-specific, so we need abstraction
 * - Other platforms (iOS, Desktop) don't need this functionality
 *
 * **Android Implementation:**
 * - Uses SavedStateHandle from ViewModel/Compose
 * - Automatically persists state across process death
 * - Restored when process is recreated
 *
 * **Other Platforms:**
 * - Use NoOpSavedStateDelegate (default)
 * - Process death is extremely rare or doesn't happen
 * - No state persistence needed
 *
 * **Usage:**
 * ```kotlin
 * // Android with ViewModel
 * class MyViewModel(
 *     grantManager: GrantManager,
 *     savedStateHandle: SavedStateHandle
 * ) : ViewModel() {
 *     val cameraGrant = GrantHandler(
 *         grantManager = grantManager,
 *         grant = AppGrant.CAMERA,
 *         scope = viewModelScope,
 *         savedStateDelegate = AndroidSavedStateDelegate(savedStateHandle)
 *     )
 * }
 *
 * // iOS or other platforms (automatic)
 * val cameraGrant = GrantHandler(
 *     grantManager = grantManager,
 *     grant = AppGrant.CAMERA,
 *     scope = scope
 *     // savedStateDelegate defaults to NoOpSavedStateDelegate
 * )
 * ```
 */
interface SavedStateDelegate {
    /**
     * Save a string value with the given key.
     * @param key Unique identifier for this value
     * @param value String to save (supports Boolean.toString(), Int.toString(), etc.)
     */
    fun saveState(key: String, value: String)

    /**
     * Restore a previously saved string value.
     * @param key The key used to save the value
     * @return The saved value, or null if not found
     */
    fun restoreState(key: String): String?

    /**
     * Clear a saved value.
     * @param key The key to clear
     */
    fun clear(key: String)
}

/**
 * Default no-op implementation for platforms that don't need state persistence.
 *
 * **Used by:**
 * - iOS (process death is extremely rare)
 * - Desktop (process is user-controlled)
 * - Any platform without SavedStateHandle equivalent
 */
class NoOpSavedStateDelegate : SavedStateDelegate {
    override fun saveState(key: String, value: String) {
        // No-op: State is not persisted
    }

    override fun restoreState(key: String): String? {
        // No-op: Always returns null (no state to restore)
        return null
    }

    override fun clear(key: String) {
        // No-op: Nothing to clear
    }
}
