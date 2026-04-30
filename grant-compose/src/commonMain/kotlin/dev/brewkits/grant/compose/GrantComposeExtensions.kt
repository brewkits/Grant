package dev.brewkits.grant.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.brewkits.grant.GrantAndServiceHandler
import dev.brewkits.grant.GrantAndServiceUiState
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantGroupUiState
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantPermission
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
 *     val state by handler.collectAsStateWithLifecycle()
 *     // Now use state.showRationale, state.rationaleMessage, etc.
 * }
 * ```
 *
 * Instead of manually calling:
 * ```kotlin
 * val state by handler.state.collectAsStateWithLifecycle()
 * ```
 *
 * @return State holder containing the current GrantUiState
 */
@Composable
fun GrantHandler.collectAsStateWithLifecycle(): State<GrantUiState> {
    return this.state.collectAsStateWithLifecycle()
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
    return this.status.collectAsStateWithLifecycle()
}

/**
 * Extension function to collect GrantGroupUiState from GrantGroupHandler in a Composable.
 *
 * **Usage**:
 * ```kotlin
 * @Composable
 * fun MyScreen(handler: GrantGroupHandler) {
 *     val state by handler.collectAsStateWithLifecycle()
 *     // Now use state.isVisible, state.grantedGrants, etc.
 * }
 * ```
 *
 * @return State holder containing the current GrantGroupUiState
 */
@Composable
fun GrantGroupHandler.collectAsStateWithLifecycle(): State<GrantGroupUiState> {
    return this.state.collectAsStateWithLifecycle()
}

/**
 * Extension function to collect all grant statuses from GrantGroupHandler in a Composable.
 *
 * **Usage**:
 * ```kotlin
 * @Composable
 * fun MyScreen(handler: GrantGroupHandler) {
 *     val statuses by handler.collectStatusesAsState()
 *     // statuses is Map<GrantPermission, GrantStatus>
 * }
 * ```
 *
 * @return State holder containing the current map of grant statuses
 */
@Composable
fun GrantGroupHandler.collectStatusesAsState(): State<Map<GrantPermission, GrantStatus>> {
    return this.statuses.collectAsStateWithLifecycle()
}

/**
 * Extension function to collect state from GrantAndServiceHandler in a Composable.
 */
@Composable
fun GrantAndServiceHandler.collectAsStateWithLifecycle(): State<GrantAndServiceUiState> {
    return this.state.collectAsStateWithLifecycle()
}
