package dev.brewkits.grant.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantUiState

/**
 * Extension function to collect GrantUiState from GrantHandler in a Composable.
 *
 * This is a convenience helper that simplifies the common pattern of observing
 * the handler's state in Compose UI.
 *
 * **Usage**:
 * ```kotlin
 * @Composable
 * fun MyScreen(handler: GrantHandler) {
 *     val state by handler.collectAsState()
 *     // Now use state.showRationale, state.rationaleMessage, etc.
 * }
 * ```
 *
 * Instead of manually calling:
 * ```kotlin
 * val state by handler.state.collectAsState()
 * ```
 *
 * @return State holder containing the current GrantUiState
 */
@Composable
fun GrantHandler.collectAsState(): State<GrantUiState> {
    return this.state.collectAsState()
}

/**
 * Extension function to collect GrantStatus from GrantHandler in a Composable.
 *
 * **Usage**:
 * ```kotlin
 * @Composable
 * fun MyScreen(handler: GrantHandler) {
 *     val status by handler.collectStatusAsState()
 *     when (status) {
 *         GrantStatus.GRANTED -> Text("Granted!")
 *         GrantStatus.DENIED -> Text("Denied")
 *         // ...
 *     }
 * }
 * ```
 *
 * @return State holder containing the current GrantStatus
 */
@Composable
fun GrantHandler.collectStatusAsState(): State<GrantStatus> {
    return this.status.collectAsState()
}
