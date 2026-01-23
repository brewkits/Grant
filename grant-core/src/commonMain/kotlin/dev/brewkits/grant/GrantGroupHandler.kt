package dev.brewkits.grant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for group grant dialogs.
 */
data class GrantGroupUiState(
    val isVisible: Boolean = false,
    val currentGrant: AppGrant? = null,
    val showRationale: Boolean = false,
    val showSettingsGuide: Boolean = false,
    val rationaleMessage: String? = null,
    val settingsMessage: String? = null,
    val grantedGrants: Set<AppGrant> = emptySet(),
    val totalGrants: Int = 0
)

/**
 * Handles multiple grants as a group.
 *
 * All grants must be granted for onAllGranted callback to execute.
 * Individual grants are requested sequentially to avoid overwhelming the user.
 *
 * **Use Cases:**
 * - Video recording: Camera + Microphone
 * - Location photos: Camera + Location
 * - Contact sync: Contacts + Storage
 * - Fitness tracking: Location + Activity Recognition
 *
 * **Benefits:**
 * - Single handler for related grants
 * - Sequential requests for better UX
 * - Progress tracking (X of Y grants granted)
 * - Custom messages per grant
 * - Automatic state management
 *
 * **Usage Example:**
 * ```kotlin
 * val videoRecordingGrants = GrantGroupHandler(
 *     grantManager = grantManager,
 *     grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
 *     scope = viewModelScope
 * )
 *
 * videoRecordingGrants.request(
 *     rationaleMessages = mapOf(
 *         AppGrant.CAMERA to "Camera needed for video",
 *         AppGrant.MICROPHONE to "Microphone needed for audio"
 *     )
 * ) {
 *     // Both granted!
 *     startVideoRecording()
 * }
 * ```
 *
 * @param grantManager The underlying grant manager
 * @param grants List of grants to request as a group
 * @param scope CoroutineScope for launching grant requests.
 *              **MUST** use viewModelScope from a ViewModel.
 *              See [GrantHandler] documentation for detailed scope requirements.
 */
class GrantGroupHandler(
    private val grantManager: GrantManager,
    private val grants: List<AppGrant>,
    scope: CoroutineScope
) {
    private val scope: CoroutineScope

    init {
        // Validate grants list
        require(grants.isNotEmpty()) {
            "GrantGroupHandler requires at least one grant. " +
            "Provided grants list is empty."
        }

        // Store the scope (validation happens implicitly when first coroutine is launched)
        this.scope = scope
    }
    private val _state = MutableStateFlow(GrantGroupUiState(totalGrants = grants.size))
    val state: StateFlow<GrantGroupUiState> = _state.asStateFlow()

    private val _statuses = MutableStateFlow<Map<AppGrant, GrantStatus>>(emptyMap())
    val statuses: StateFlow<Map<AppGrant, GrantStatus>> = _statuses.asStateFlow()

    private var onAllGrantedCallback: (() -> Unit)? = null
    private var currentRationaleMessages: Map<AppGrant, String> = emptyMap()
    private var currentSettingsMessages: Map<AppGrant, String> = emptyMap()

    init {
        refreshAllStatuses()
    }

    /**
     * Refresh all grant statuses.
     * Call this after user returns from Settings.
     */
    fun refreshAllStatuses() {
        scope.launch {
            val statusMap = mutableMapOf<AppGrant, GrantStatus>()
            val grantedSet = mutableSetOf<AppGrant>()

            for (grant in grants) {
                val status = grantManager.checkStatus(grant)
                statusMap[grant] = status
                if (status == GrantStatus.GRANTED) {
                    grantedSet.add(grant)
                }
            }

            _statuses.value = statusMap
            _state.update { it.copy(grantedGrants = grantedSet) }
        }
    }

    /**
     * Request all grants in the group.
     *
     * Grants are requested sequentially for better UX.
     * If user denies one grant, the flow stops and shows appropriate dialog.
     *
     * @param rationaleMessages Custom messages per grant for rationale dialog
     * @param settingsMessages Custom messages per grant for settings dialog
     * @param onAllGranted Callback that executes ONLY when all grants are granted
     */
    fun request(
        rationaleMessages: Map<AppGrant, String> = emptyMap(),
        settingsMessages: Map<AppGrant, String> = emptyMap(),
        onAllGranted: () -> Unit
    ) {
        this.onAllGrantedCallback = onAllGranted
        this.currentRationaleMessages = rationaleMessages
        this.currentSettingsMessages = settingsMessages

        scope.launch {
            val deniedGrants = mutableListOf<AppGrant>()

            // Check which grants need to be requested
            for (grant in grants) {
                val status = grantManager.checkStatus(grant)
                _statuses.value = _statuses.value + (grant to status)

                if (status != GrantStatus.GRANTED) {
                    deniedGrants.add(grant)
                }
            }

            if (deniedGrants.isEmpty()) {
                // All already granted!
                _state.update { it.copy(grantedGrants = grants.toSet()) }
                onAllGranted()
                return@launch
            }

            // Request each denied grant sequentially
            for (grant in deniedGrants) {
                val granted = requestSingleGrant(grant)

                if (granted) {
                    // Update granted set
                    _state.update {
                        it.copy(grantedGrants = it.grantedGrants + grant)
                    }
                } else {
                    // User denied, stop the flow
                    return@launch
                }
            }

            // All granted!
            onAllGranted()
        }
    }

    /**
     * Called when user confirms rationale dialog.
     */
    fun onRationaleConfirmed() {
        scope.launch {
            val currentPerm = _state.value.currentGrant ?: return@launch

            val newStatus = grantManager.request(currentPerm)
            _statuses.value = _statuses.value + (currentPerm to newStatus)

            handleStatus(currentPerm, newStatus)
        }
    }

    /**
     * Called when user clicks "Open Settings".
     */
    fun onSettingsConfirmed() {
        resetState()
        grantManager.openSettings()
    }

    /**
     * Called when user dismisses any dialog.
     */
    fun onDismiss() {
        resetState()
    }

    // --- Internal Logic ---

    private suspend fun requestSingleGrant(grant: AppGrant): Boolean {
        val currentStatus = grantManager.checkStatus(grant)
        _statuses.value = _statuses.value + (grant to currentStatus)

        return when (currentStatus) {
            GrantStatus.GRANTED -> true
            GrantStatus.NOT_DETERMINED -> {
                // First time - request immediately
                val result = grantManager.request(grant)
                _statuses.value = _statuses.value + (grant to result)
                handleStatus(grant, result)
                result == GrantStatus.GRANTED
            }
            GrantStatus.DENIED,
            GrantStatus.DENIED_ALWAYS -> {
                // Show appropriate dialog
                handleStatus(grant, currentStatus)
                false
            }
        }
    }

    private fun handleStatus(grant: AppGrant, status: GrantStatus): Boolean {
        return when (status) {
            GrantStatus.GRANTED -> true
            GrantStatus.NOT_DETERMINED -> {
                // This shouldn't happen as we handle it above
                false
            }
            GrantStatus.DENIED -> {
                // Show rationale
                _state.update {
                    it.copy(
                        isVisible = true,
                        currentGrant = grant,
                        showRationale = true,
                        showSettingsGuide = false,
                        rationaleMessage = currentRationaleMessages[grant]
                    )
                }
                false
            }
            GrantStatus.DENIED_ALWAYS -> {
                // Show settings guide
                _state.update {
                    it.copy(
                        isVisible = true,
                        currentGrant = grant,
                        showRationale = false,
                        showSettingsGuide = true,
                        settingsMessage = currentSettingsMessages[grant]
                    )
                }
                false
            }
        }
    }

    private fun resetState() {
        _state.update {
            it.copy(
                isVisible = false,
                currentGrant = null,
                showRationale = false,
                showSettingsGuide = false,
                rationaleMessage = null,
                settingsMessage = null
            )
        }
    }
}
