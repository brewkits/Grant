package dev.brewkits.grant

import androidx.lifecycle.SavedStateHandle

/**
 * Android implementation of SavedStateDelegate using SavedStateHandle.
 *
 * **What is SavedStateHandle?**
 * - Part of Android's ViewModel architecture
 * - Automatically persists data across process death
 * - Survives configuration changes (rotation, etc.)
 * - Backed by Bundle (same mechanism as onSaveInstanceState)
 *
 * **How Process Death Recovery Works:**
 * 1. User opens permission dialog
 * 2. Android kills process to reclaim memory
 * 3. SavedStateHandle automatically saves GrantHandler state
 * 4. User returns to app
 * 5. Android recreates process
 * 6. SavedStateHandle restores state
 * 7. Dialog reappears with same content
 *
 * **Usage:**
 * ```kotlin
 * class MyViewModel(
 *     private val grantManager: GrantManager,
 *     private val savedStateHandle: SavedStateHandle // Injected by ViewModel
 * ) : ViewModel() {
 *     val cameraGrant = GrantHandler(
 *         grantManager = grantManager,
 *         grant = AppGrant.CAMERA,
 *         scope = viewModelScope,
 *         savedStateDelegate = AndroidSavedStateDelegate(savedStateHandle)
 *     )
 * }
 * ```
 *
 * **Benefits:**
 * - Zero boilerplate for users
 * - Automatic state restoration
 * - Works with Jetpack Compose + Navigation
 * - Type-safe with SavedStateHandle
 *
 * @param savedStateHandle The SavedStateHandle from ViewModel
 */
class AndroidSavedStateDelegate(
    private val savedStateHandle: SavedStateHandle
) : SavedStateDelegate {

    override fun saveState(key: String, value: String) {
        savedStateHandle[key] = value
    }

    override fun restoreState(key: String): String? {
        return savedStateHandle[key]
    }

    override fun clear(key: String) {
        savedStateHandle.remove<String>(key)
    }
}
