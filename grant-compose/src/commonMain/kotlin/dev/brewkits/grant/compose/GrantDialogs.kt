package dev.brewkits.grant.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import androidx.compose.runtime.State

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
    val state by handler.state.collectAsState()
    
    // Performance Optimization: Cache visibility logic to prevent redundant recompositions
    val isVisible by remember { derivedStateOf { state.isVisible } }
    val showRationale by remember { derivedStateOf { state.showRationale } }
    val showSettingsGuide by remember { derivedStateOf { state.showSettingsGuide } }

    if (isVisible) {
        when {
            showRationale -> {
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

            showSettingsGuide -> {
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
    val state by handler.state.collectAsState()
    
    val isVisible by remember { derivedStateOf { state.isVisible } }
    val showRationale by remember { derivedStateOf { state.showRationale } }
    val showSettingsGuide by remember { derivedStateOf { state.showSettingsGuide } }

    if (isVisible) {
        when {
            showRationale -> {
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

            showSettingsGuide -> {
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
 * Rationale Dialog with support for long text (scrollable) and accessibility.
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
        title = { 
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()) // Compatibility: support small screens & large fonts
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.semantics { contentDescription = confirmText }
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = dismissText }
            ) {
                Text(text = dismissText)
            }
        }
    )
}

/**
 * Settings Guide Dialog with support for long text (scrollable) and accessibility.
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
        title = { 
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.semantics { contentDescription = confirmText }
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = dismissText }
            ) {
                Text(text = dismissText)
            }
        }
    )
}
