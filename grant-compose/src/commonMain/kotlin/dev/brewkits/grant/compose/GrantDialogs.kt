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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.brewkits.grant.GrantAndServiceHandler
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler

/**
 * A comprehensive Dialog Handler optimized for performance and accessibility.
 * 
 * Uses derivedStateOf to minimize recompositions and supports scrollable 
 * content for small screen compatibility.
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
 * A Dialog Handler for group permission requests, optimized for performance.
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
 * A Dialog Handler for unified permission and hardware service requests.
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

            state.showPermissionSettings -> {
                GrantSettingsDialog(
                    message = state.permissionSettingsMessage
                        ?: "This permission was denied. Please enable it in Settings.",
                    title = permissionSettingsTitle,
                    confirmText = permissionSettingsConfirm,
                    dismissText = dismissText,
                    onConfirm = { handler.onPermissionSettingsConfirmed() },
                    onDismiss = { handler.onDismiss() }
                )
            }
            
            state.showServiceSettings -> {
                GrantSettingsDialog(
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
