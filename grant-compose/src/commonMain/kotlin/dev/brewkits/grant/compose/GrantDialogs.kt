package dev.brewkits.grant.compose

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.brewkits.grant.GrantHandler

/**
 * A comprehensive Dialog Handler that automatically shows Rationale or Settings dialogs
 * based on the GrantHandler state.
 *
 * Just drop this into your Composable hierarchy (e.g., at the root of a Screen).
 * It observes the GrantHandler's state and automatically shows the appropriate dialog.
 *
 * **Usage (Basic)**:
 * ```kotlin
 * @Composable
 * fun MyScreen(viewModel: MyViewModel) {
 *     // Just add this one line - handles all dialogs automatically
 *     GrantDialog(handler = viewModel.cameraGrant)
 *
 *     // Your UI
 *     Button(onClick = { viewModel.cameraGrant.request { openCamera() } }) {
 *         Text("Take Photo")
 *     }
 * }
 * ```
 *
 * **Usage (Custom Messages)**:
 * ```kotlin
 * GrantDialog(
 *     handler = viewModel.locationGrant,
 *     rationaleTitle = "Location Required",
 *     rationaleConfirm = "Allow",
 *     settingsTitle = "Enable Location",
 *     settingsConfirm = "Go to Settings"
 * )
 * ```
 *
 * **Architecture Note**:
 * This component follows the "Headless UI" pattern:
 * - Logic is in GrantHandler (grant-core)
 * - UI is in this component (grant-compose)
 * - Both are decoupled and testable independently
 *
 * @param handler The GrantHandler from your ViewModel
 * @param rationaleTitle Title for the rationale dialog (default: "Permission Required")
 * @param rationaleConfirm Text for rationale confirm button (default: "Continue")
 * @param rationaleDismiss Text for rationale dismiss button (default: "Cancel")
 * @param settingsTitle Title for the settings dialog (default: "Permission Denied")
 * @param settingsConfirm Text for settings confirm button (default: "Open Settings")
 * @param settingsDismiss Text for settings dismiss button (default: "Cancel")
 */
@Composable
fun GrantDialog(
    handler: GrantHandler,
    rationaleTitle: String = "Permission Required",
    rationaleConfirm: String = "Continue",
    rationaleDismiss: String = "Cancel",
    settingsTitle: String = "Permission Denied",
    settingsConfirm: String = "Open Settings",
    settingsDismiss: String = "Cancel"
) {
    val state by handler.collectAsState()

    if (state.isVisible) {
        when {
            state.showRationale -> {
                GrantRationaleDialog(
                    message = state.rationaleMessage
                        ?: "This permission is needed for this feature to work properly.",
                    title = rationaleTitle,
                    confirmText = rationaleConfirm,
                    dismissText = rationaleDismiss,
                    onConfirm = { handler.onRationaleConfirmed() },
                    onDismiss = { handler.onDismiss() }
                )
            }

            state.showSettingsGuide -> {
                GrantSettingsDialog(
                    message = state.settingsMessage
                        ?: "This permission was denied. Please enable it in Settings.",
                    title = settingsTitle,
                    confirmText = settingsConfirm,
                    dismissText = settingsDismiss,
                    onConfirm = { handler.onSettingsConfirmed() },
                    onDismiss = { handler.onDismiss() }
                )
            }
        }
    }
}

/**
 * Rationale Dialog shown when user denies permission for the first time.
 *
 * This dialog explains WHY the permission is needed and gives user
 * the option to grant it or cancel.
 *
 * **Note**: Typically you should use [GrantDialog] instead of calling this directly.
 * This component is public for advanced customization scenarios.
 *
 * @param message The rationale message explaining why permission is needed
 * @param title Dialog title (default: "Permission Required")
 * @param confirmText Text for confirm button (default: "Continue")
 * @param dismissText Text for dismiss button (default: "Cancel")
 * @param onConfirm Called when user confirms (will trigger permission request)
 * @param onDismiss Called when user dismisses (will cancel permission flow)
 */
@Composable
fun GrantRationaleDialog(
    message: String,
    title: String = "Permission Required",
    confirmText: String = "Continue",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}

/**
 * Settings Guide Dialog shown when permission is permanently denied.
 *
 * This dialog informs the user that permission was denied and they
 * need to enable it manually in system Settings.
 *
 * **Note**: Typically you should use [GrantDialog] instead of calling this directly.
 * This component is public for advanced customization scenarios.
 *
 * @param message The settings message explaining how to enable permission
 * @param title Dialog title (default: "Permission Denied")
 * @param confirmText Text for confirm button (default: "Open Settings")
 * @param dismissText Text for dismiss button (default: "Cancel")
 * @param onConfirm Called when user confirms (will open system Settings)
 * @param onDismiss Called when user dismisses (will cancel permission flow)
 */
@Composable
fun GrantSettingsDialog(
    message: String,
    title: String = "Permission Denied",
    confirmText: String = "Open Settings",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}
