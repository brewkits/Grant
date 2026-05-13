package dev.brewkits.grant.demo

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable to bind the permission launcher to [GrantManager].
 *
 * - **Android**: Registers an [ActivityResultLauncher] and sets it on [GrantManager] so
 *   that [GrantManager.request] can show system permission dialogs.
 * - **iOS**: No-op — CoreLocation/AVFoundation/etc. delegates handle dialogs natively.
 */
@Composable
expect fun BindGrantsController()
