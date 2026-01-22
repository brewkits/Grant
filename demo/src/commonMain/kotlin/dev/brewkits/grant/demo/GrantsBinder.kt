package dev.brewkits.grant.demo

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable to bind GrantsController to Activity lifecycle.
 *
 * - **Android**: No-op, handled by GrantRequestActivity
 * - **iOS**: No-op, handled by platform delegates
 */
@Composable
expect fun BindGrantsController()
