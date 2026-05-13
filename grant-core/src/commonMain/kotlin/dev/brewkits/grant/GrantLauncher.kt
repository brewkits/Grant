package dev.brewkits.grant

/**
 * Interface for platform-specific permission launchers.
 * 
 * Implementations of this interface should encapsulate the platform-specific
 * mechanism for requesting permissions (e.g., ActivityResultLauncher on Android,
 * PHPhotoLibrary.requestAuthorization on iOS).
 */
interface GrantLauncher {
    /**
     * Launch the permission request for the given permissions.
     *
     * @param permissions The list of permissions to request.
     * @param onResult The callback invoked with the request results.
     */
    fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit)
}
