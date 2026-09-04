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

internal enum class DialogKind { None, Rationale, Settings }
internal enum class ServiceDialogKind { None, Rationale, PermissionSettings, ServiceSettings }

/**
 * Which dialog a [GrantHandler]/[GrantGroupHandler] state should show.
 *
 * Extracted from the `derivedStateOf` blocks below so it can be tested. It takes the three
 * flags rather than a state object because `GrantUiState` and `GrantGroupUiState` are separate
 * data classes with no shared supertype — passing booleans is what lets one function serve
 * both, and keeps it free of any Compose dependency.
 *
 * **The ordering is the point, not the mapping.** `showRationale` is checked before
 * `showSettingsGuide`: a state carrying both must show the rationale, because rationale is the
 * recoverable step and the settings guide is the terminal one. Getting that backwards sends a
 * user who could still be persuaded straight to a Settings screen — the failure shape behind
 * Issue #55 and Issue #41. Nothing verified this ordering before; `GrantDialogKindTest` does.
 */
internal fun resolveDialogKind(
    isVisible: Boolean,
    showRationale: Boolean,
    showSettingsGuide: Boolean,
): DialogKind = when {
    !isVisible -> DialogKind.None
    showRationale -> DialogKind.Rationale
    showSettingsGuide -> DialogKind.Settings
    else -> DialogKind.None
}

/**
 * The [GrantAndServiceHandler] equivalent of [resolveDialogKind], with the extra distinction
 * between a *permission* settings screen and a *service* one (e.g. GPS switched off).
 *
 * Same ordering rule, extended: rationale first, then permission settings, then service
 * settings. Permission precedes service because a missing permission blocks the feature
 * outright, while a disabled service is recoverable without any grant — showing the service
 * prompt first would ask the user to fix the lesser problem.
 */
internal fun resolveServiceDialogKind(
    isVisible: Boolean,
    showRationale: Boolean,
    showPermissionSettings: Boolean,
    showServiceSettings: Boolean,
): ServiceDialogKind = when {
    !isVisible -> ServiceDialogKind.None
    showRationale -> ServiceDialogKind.Rationale
    showPermissionSettings -> ServiceDialogKind.PermissionSettings
    showServiceSettings -> ServiceDialogKind.ServiceSettings
    else -> ServiceDialogKind.None
}

/**
 * A comprehensive Dialog Handler optimized for performance and accessibility.
 *
 * Button labels and titles are resolved from [LocalGrantDialogStrings] — set them
 * once at the top of your composition via [GrantDialogStringsProvider] and every
 * `GrantDialog` call in the subtree picks them up automatically.
 *
 * An explicit [strings] parameter overrides [LocalGrantDialogStrings] for this
 * single call site, useful when one screen needs a different tone or wording.
 *
 * Uses [derivedStateOf] over the underlying [GrantHandler.state] so that
 * unrelated field changes (e.g. message text edits) do not trigger a
 * re-composition of the dialog branch selection. Recomposition only happens
 * when the dialog kind actually changes.
 */
@Composable
public fun GrantDialog(
    handler: GrantHandler,
    strings: GrantDialogStrings = LocalGrantDialogStrings.current,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        handler.refreshStatus()
    }

    val state by handler.collectAsStateWithLifecycle()
    val dialogKind by remember {
        derivedStateOf {
            resolveDialogKind(state.isVisible, state.showRationale, state.showSettingsGuide)
        }
    }

    when (dialogKind) {
        DialogKind.None -> Unit
        DialogKind.Rationale -> GrantRationaleDialog(
            message = state.rationaleMessage
                ?: strings.rationaleMessage,
            title = strings.rationaleTitle,
            confirmText = strings.rationaleConfirm,
            dismissText = strings.rationaleDismiss,
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        DialogKind.Settings -> GrantSettingsDialog(
            message = state.settingsMessage
                ?: strings.settingsMessage,
            title = strings.settingsTitle,
            confirmText = strings.settingsConfirm,
            dismissText = strings.settingsDismiss,
            onConfirm = { handler.onSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }
}

/**
 * A Dialog Handler for group permission requests.
 *
 * Button labels and titles are resolved from [LocalGrantDialogStrings].
 * See [GrantDialog] for usage details.
 *
 * Uses [derivedStateOf] so that mid-flight `grantedGrants` set updates (which
 * tick frequently while the user grants each permission) do not invalidate the
 * dialog branch unless the visible dialog kind actually changes.
 */
@Composable
public fun GrantGroupDialog(
    handler: GrantGroupHandler,
    strings: GrantDialogStrings = LocalGrantDialogStrings.current,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        handler.refreshAllStatuses()
    }

    val state by handler.collectAsStateWithLifecycle()
    val dialogKind by remember {
        derivedStateOf {
            resolveDialogKind(state.isVisible, state.showRationale, state.showSettingsGuide)
        }
    }

    when (dialogKind) {
        DialogKind.None -> Unit
        DialogKind.Rationale -> GrantRationaleDialog(
            message = state.rationaleMessage
                ?: strings.rationaleMessage,
            title = strings.rationaleTitle,
            confirmText = strings.rationaleConfirm,
            dismissText = strings.rationaleDismiss,
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        DialogKind.Settings -> GrantSettingsDialog(
            message = state.settingsMessage
                ?: strings.settingsMessage,
            title = strings.settingsTitle,
            confirmText = strings.settingsConfirm,
            dismissText = strings.settingsDismiss,
            onConfirm = { handler.onSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }
}

/**
 * A Dialog Handler for unified permission and hardware service requests.
 *
 * Button labels and titles are resolved from [LocalGrantDialogStrings].
 * See [GrantDialog] for usage details.
 *
 * Uses [derivedStateOf] to isolate dialog-kind changes from unrelated state
 * updates such as service availability ticks.
 */
@Composable
public fun GrantAndServiceDialog(
    handler: GrantAndServiceHandler,
    strings: GrantDialogStrings = LocalGrantDialogStrings.current,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        handler.refreshStatus()
    }

    val state by handler.collectAsStateWithLifecycle()
    val dialogKind by remember {
        derivedStateOf {
            resolveServiceDialogKind(
                state.isVisible,
                state.showRationale,
                state.showPermissionSettings,
                state.showServiceSettings,
            )
        }
    }

    when (dialogKind) {
        ServiceDialogKind.None -> Unit
        ServiceDialogKind.Rationale -> GrantRationaleDialog(
            message = state.rationaleMessage
                ?: strings.rationaleMessage,
            title = strings.rationaleTitle,
            confirmText = strings.rationaleConfirm,
            dismissText = strings.rationaleDismiss,
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        ServiceDialogKind.PermissionSettings -> GrantSettingsDialog(
            message = state.permissionSettingsMessage
                ?: strings.settingsMessage,
            title = strings.settingsTitle,
            confirmText = strings.settingsConfirm,
            dismissText = strings.settingsDismiss,
            onConfirm = { handler.onPermissionSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
        ServiceDialogKind.ServiceSettings -> GrantSettingsDialog(
            message = state.serviceSettingsMessage
                ?: strings.serviceSettingsMessage,
            title = strings.serviceSettingsTitle,
            confirmText = strings.serviceSettingsConfirm,
            dismissText = strings.serviceSettingsDismiss,
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
public fun GrantRationaleDialog(
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
public fun GrantSettingsDialog(
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
