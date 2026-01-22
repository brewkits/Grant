package dev.brewkits.grant.demo

import androidx.compose.runtime.Composable

/**
 * iOS implementation of GrantsBinder.
 *
 * No-op on iOS as grant requests are handled by platform delegates.
 */
@Composable
actual fun BindGrantsController() {
    // No binding needed on iOS
}
