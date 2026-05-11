package dev.brewkits.grant.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.brewkits.grant.GrantAndServiceHandler
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler

private enum class DialogKind { None, Rationale, Settings }
private enum class ServiceDialogKind { None, Rationale, PermissionSettings, ServiceSettings }

/**
 * A comprehensive Dialog Handler optimized for performance and accessibility.
 *
 * Uses [derivedStateOf] over the underlying [GrantHandler.state] so that
 * unrelated field changes (e.g. message text edits) do not trigger a
 * re-composition of the dialog branch selection. Recomposition only happens
 * when the dialog kind actually changes.
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        handler.refreshStatus()
    }

    val state by handler.collectAsStateWithLifecycle()
    val dialogKind by remember {
        derivedStateOf {
            when {
                !state.isVisible -> DialogKind.None
                state.showRationale -> DialogKind.Rationale
                state.showSettingsGuide -> DialogKind.Settings
                else -> DialogKind.None
            }
        }
    }

    when (dialogKind) {
        DialogKind.None -> Unit
        DialogKind.Rationale -> GrantRationaleDialog(
            message = state.rationaleMessage
                ?: "This permission is needed for this feature to work properly.",
            title = rationaleTitle,
            confirmText = rationaleConfirm,
            dismissText = rationaleDismiss,
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        DialogKind.Settings -> GrantSettingsDialog(
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

/**
 * A Dialog Handler for group permission requests.
 *
 * Uses [derivedStateOf] so that mid-flight `grantedGrants` set updates (which
 * tick frequently while the user grants each permission) do not invalidate the
 * dialog branch unless the visible dialog kind actually changes.
 */
@Composable
fun GrantGroupDialog(
    handler: GrantGroupHandler,
    rationaleTitle: String = "Permission Required",
    rationaleConfirm: String = "Continue",
    rationaleDismiss: String = "Cancel",
    settingsTitle: String = "Permission Denied",
    settingsConfirm: String = "Open Settings",
    settingsDismiss: String = "Cancel"
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        handler.refreshAllStatuses()
    }

    val state by handler.collectAsStateWithLifecycle()
    val dialogKind by remember {
        derivedStateOf {
            when {
                !state.isVisible -> DialogKind.None
                state.showRationale -> DialogKind.Rationale
                state.showSettingsGuide -> DialogKind.Settings
                else -> DialogKind.None
            }
        }
    }

    when (dialogKind) {
        DialogKind.None -> Unit
        DialogKind.Rationale -> GrantRationaleDialog(
            message = state.rationaleMessage
                ?: "This permission is needed for this feature to work properly.",
            title = rationaleTitle,
            confirmText = rationaleConfirm,
            dismissText = rationaleDismiss,
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        DialogKind.Settings -> GrantSettingsDialog(
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

/**
 * A Dialog Handler for unified permission and hardware service requests.
 *
 * Uses [derivedStateOf] to isolate dialog-kind changes from unrelated state
 * updates such as service availability ticks.
 */
@Composable
fun GrantAndServiceDialog(
    handler: GrantAndServiceHandler,
    rationaleTitle: String = "Permission Required",
    rationaleConfirm: String = "Continue",
    rationaleDismiss: String = "Cancel",
    permissionSettingsTitle: String = "Permission Denied",
    permissionSettingsConfirm: String = "Open Settings",
    serviceSettingsTitle: String = "Service Required",
    serviceSettingsConfirm: String = "Enable Service",
    dismissText: String = "Cancel"
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        handler.refreshStatus()
    }

    val state by handler.collectAsStateWithLifecycle()
    val dialogKind by remember {
        derivedStateOf {
            when {
                !state.isVisible -> ServiceDialogKind.None
                state.showRationale -> ServiceDialogKind.Rationale
                state.showPermissionSettings -> ServiceDialogKind.PermissionSettings
                state.showServiceSettings -> ServiceDialogKind.ServiceSettings
                else -> ServiceDialogKind.None
            }
        }
    }

    when (dialogKind) {
        ServiceDialogKind.None -> Unit
        ServiceDialogKind.Rationale -> GrantRationaleDialog(
            message = state.rationaleMessage
                ?: "This permission is needed for this feature to work properly.",
            title = rationaleTitle,
            confirmText = rationaleConfirm,
            dismissText = rationaleDismiss,
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        ServiceDialogKind.PermissionSettings -> GrantSettingsDialog(
            message = state.permissionSettingsMessage
                ?: "This permission was denied. Please enable it in Settings.",
            title = permissionSettingsTitle,
            confirmText = permissionSettingsConfirm,
            dismissText = dismissText,
            onConfirm = { handler.onPermissionSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        ServiceDialogKind.ServiceSettings -> GrantSettingsDialog(
            message = state.serviceSettingsMessage
                ?: "This service needs to be enabled for this feature to work.",
            title = serviceSettingsTitle,
            confirmText = serviceSettingsConfirm,
            dismissText = dismissText,
            onConfirm = { handler.onServiceSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }
}

/**
 * Rationale Dialog with support for long text (scrollable) and accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrantRationaleDialog(
    message: String,
    title: String = "Permission Required",
    confirmText: String = "Continue",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = dismissText }) {
                        Text(text = dismissText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm, modifier = Modifier.semantics { contentDescription = confirmText }) {
                        Text(text = confirmText)
                    }
                }
            }
        }
    }
}

/**
 * Settings Guide Dialog with support for long text (scrollable) and accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrantSettingsDialog(
    message: String,
    title: String = "Permission Denied",
    confirmText: String = "Open Settings",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = dismissText }) {
                        Text(text = dismissText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm, modifier = Modifier.semantics { contentDescription = confirmText }) {
                        Text(text = confirmText)
                    }
                }
            }
        }
    }
}
