package dev.brewkits.grant

/**
 * A platform-agnostic interface for persisting UI state across system-initiated
 * process death.
 *
 * This is primarily used on Android to ensure that rationale or settings guide
 * dialogs remain visible if the OS kills the app process while the user is away.
 */
interface SavedStateDelegate {
    /**
     * Persists a state value.
     */
    fun saveState(key: String, value: String)

    /**
     * Retrieves a persisted state value.
     */
    fun restoreState(key: String): String?

    /**
     * Clears a persisted state value.
     */
    fun clear(key: String)
}

/**
 * A no-op implementation used on platforms where process death recovery is not
 * required (e.g., iOS, Desktop).
 */
class NoOpSavedStateDelegate : SavedStateDelegate {
    override fun saveState(key: String, value: String) {}
    override fun restoreState(key: String): String? = null
    override fun clear(key: String) {}
}
