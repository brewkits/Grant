package dev.brewkits.grant.demo

import androidx.compose.runtime.Composable

/**
 * Android implementation of GrantsBinder.
 *
 * CUSTOM implementation doesn't need binding (uses transparent Activity approach).
 * No-op on Android since grants are handled by GrantRequestActivity.
 */
@Composable
actual fun BindGrantsController() {
    // CUSTOM implementation doesn't need binding - it's handled by GrantRequestActivity
    // No-op
}
