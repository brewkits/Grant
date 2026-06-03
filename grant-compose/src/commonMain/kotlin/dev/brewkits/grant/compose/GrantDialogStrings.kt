package dev.brewkits.grant.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * All user-visible strings shown by [GrantDialog], [GrantGroupDialog], and
 * [GrantAndServiceDialog].
 *
 * **The library ships English defaults as a last-resort fallback only.**
 * Host apps own the translation responsibility — set your strings once at the
 * top of the composition tree via [GrantDialogStringsProvider] and every
 * `GrantDialog` call in the subtree picks them up automatically.
 *
 * ```kotlin
 * // In your app theme / root composable — do this ONCE:
 * GrantDialogStringsProvider(
 *     strings = GrantDialogStrings(
 *         rationaleTitle   = stringResource(Res.string.grant_rationale_title),
 *         rationaleConfirm = stringResource(Res.string.grant_ok),
 *         rationaleDismiss = stringResource(Res.string.grant_cancel),
 *         settingsTitle    = stringResource(Res.string.grant_settings_title),
 *         settingsConfirm  = stringResource(Res.string.grant_open_settings),
 *         settingsDismiss  = stringResource(Res.string.grant_cancel),
 *     )
 * ) {
 *     // Every GrantDialog() call inside here uses the strings above.
 *     MyAppContent()
 * }
 * ```
 *
 * Individual call sites can still override a single dialog's text by passing
 * a `strings` parameter directly to [GrantDialog].
 */
@Immutable
data class GrantDialogStrings(
    val rationaleTitle: String = "Permission Required",
    val rationaleConfirm: String = "Continue",
    val rationaleDismiss: String = "Cancel",
    val settingsTitle: String = "Permission Denied",
    val settingsConfirm: String = "Open Settings",
    val settingsDismiss: String = "Cancel",
    val serviceSettingsTitle: String = "Service Required",
    val serviceSettingsConfirm: String = "Enable Service",
    val serviceSettingsDismiss: String = "Cancel",
    /** Shown in the rationale dialog body when the caller does not supply a rationaleMessage. */
    val rationaleMessage: String = "This permission is needed for this feature to work properly.",
    /** Shown in the settings guide body when the caller does not supply a settingsMessage. */
    val settingsMessage: String = "This permission was denied. Please enable it in Settings.",
    /** Shown in the service-settings guide body when the caller does not supply a serviceSettingsMessage. */
    val serviceSettingsMessage: String = "This service needs to be enabled for this feature to work.",
)

/**
 * CompositionLocal that carries [GrantDialogStrings] down the composition tree.
 *
 * Default value is the English [GrantDialogStrings] — used when no
 * [GrantDialogStringsProvider] ancestor is present.
 */
val LocalGrantDialogStrings: ProvidableCompositionLocal<GrantDialogStrings> =
    staticCompositionLocalOf { GrantDialogStrings() }

/**
 * Provides [strings] to all [GrantDialog] / [GrantGroupDialog] /
 * [GrantAndServiceDialog] composables in [content].
 *
 * Place this once inside your app theme or root composable.
 */
@Composable
fun GrantDialogStringsProvider(
    strings: GrantDialogStrings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGrantDialogStrings provides strings,
        content = content,
    )
}
