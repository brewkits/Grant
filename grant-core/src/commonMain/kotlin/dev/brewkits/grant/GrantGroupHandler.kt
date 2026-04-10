package dev.brewkits.grant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UI State for group grant dialogs.
 */
data class GrantGroupUiState(
    val isVisible: Boolean = false,
    val currentGrant: GrantPermission? = null,
    val showRationale: Boolean = false,
    val showSettingsGuide: Boolean = false,
    val rationaleMessage: String? = null,
    val settingsMessage: String? = null,
    val grantedGrants: Set<GrantPermission> = emptySet(),
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
    private val grants: List<GrantPermission>,
    private val scope: CoroutineScope
) {
    init {
        // Validate grants list
        require(grants.isNotEmpty()) {
            "GrantGroupHandler requires at least one grant. " +
            "Provided grants list is empty."
        }
    }
    private val _state = MutableStateFlow(GrantGroupUiState(totalGrants = grants.size))
    val state: StateFlow<GrantGroupUiState> = _state.asStateFlow()

    private val _statuses = MutableStateFlow<Map<GrantPermission, GrantStatus>>(emptyMap())
    val statuses: StateFlow<Map<GrantPermission, GrantStatus>> = _statuses.asStateFlow()

    private var onAllGrantedCallback: (() -> Unit)? = null
    private var currentRationaleMessages: Map<GrantPermission, String> = emptyMap()
    private var currentSettingsMessages: Map<GrantPermission, String> = emptyMap()

    /**
     * FIX M6: Guard against double-tap / parallel group requests.
     */
    private val requestMutex = Mutex()

    init {
        refreshAllStatuses()
    }

    /**
     * Refresh all grant statuses.
     *
     * FIX: Uses async to check all permissions in parallel for better performance.
     */
    fun refreshAllStatuses() {
        scope.launch {
            val statusMap = coroutineScope {
                grants.map { grant ->
                    async { grant to grantManager.checkStatus(grant) }
                }.awaitAll().toMap()
            }

            val grantedSet = statusMap.filter { it.value == GrantStatus.GRANTED }.keys

            _statuses.value = statusMap
            _state.update { it.copy(grantedGrants = grantedSet) }
        }
    }

    /**
     * Request all grants in the group.
     */
    fun request(
        rationaleMessages: Map<GrantPermission, String> = emptyMap(),
        settingsMessages: Map<GrantPermission, String> = emptyMap(),
        onAllGranted: () -> Unit
    ) {
        this.onAllGrantedCallback = onAllGranted
        this.currentRationaleMessages = rationaleMessages
        this.currentSettingsMessages = settingsMessages

        // FIX M6: Guard against rapid taps/concurrent requests
        if (requestMutex.isLocked) return

        scope.launch {
            requestMutex.withLock {
                // 1. Initial check: What is already granted? (Parallel)
                val currentStatuses = coroutineScope {
                    grants.map { grant ->
                        async { grant to grantManager.checkStatus(grant) }
                    }.awaitAll().toMap()
                }

                val deniedGrants = currentStatuses.filter {
                    it.value != GrantStatus.GRANTED && it.value != GrantStatus.PARTIAL_GRANTED
                }.keys.toList()

                _statuses.update { it + currentStatuses }

                if (deniedGrants.isEmpty()) {
                    // Everything already good!
                    resetState()
                    _state.update { it.copy(grantedGrants = grants.toSet()) }
                    val callback = onAllGrantedCallback
                    onAllGrantedCallback = null
                    callback?.invoke()
                    return@withLock
                }

                // 2. Request all denied grants at once
                val results = grantManager.request(deniedGrants)
                _statuses.update { it + results }

                // 3. Evaluate results
                val newlyGranted = results.filter { it.value == GrantStatus.GRANTED || it.value == GrantStatus.PARTIAL_GRANTED }.keys
                _state.update { it.copy(grantedGrants = it.grantedGrants + newlyGranted) }

                val stillDenied = results.filter { it.value != GrantStatus.GRANTED && it.value != GrantStatus.PARTIAL_GRANTED }

                if (stillDenied.isEmpty()) {
                    resetState()
                    val callback = onAllGrantedCallback
                    onAllGrantedCallback = null
                    callback?.invoke()
                } else {
                    // Show dialog for the first denied permission
                    val firstDenied = stillDenied.keys.first()
                    handleStatus(firstDenied, stillDenied[firstDenied]!!)
                }
            }
        }
    }

    /**
     * Called when user confirms rationale dialog.
     */
    fun onRationaleConfirmed() {
        // FIX M6: Guard against rapid taps/concurrent requests
        if (requestMutex.isLocked) return

        scope.launch {
            requestMutex.withLock {
                val currentPerm = _state.value.currentGrant ?: return@withLock

                val newStatus = grantManager.request(currentPerm)
                _statuses.update { it + (currentPerm to newStatus) }

                if (newStatus == GrantStatus.GRANTED || newStatus == GrantStatus.PARTIAL_GRANTED) {
                    // Update granted set
                    _state.update { it.copy(grantedGrants = it.grantedGrants + currentPerm) }
                    
                    // Re-evaluate if there are any remaining denied permissions in the group
                    val stillDenied = _statuses.value.filter { 
                        grants.contains(it.key) && it.value != GrantStatus.GRANTED && it.value != GrantStatus.PARTIAL_GRANTED 
                    }
                    
                    if (stillDenied.isEmpty()) {
                        resetState()
                        val callback = onAllGrantedCallback
                        onAllGrantedCallback = null
                        callback?.invoke()
                    } else {
                        // Show dialog for the next denied permission
                        val nextDenied = stillDenied.keys.first()
                        handleStatus(nextDenied, stillDenied[nextDenied]!!)
                    }
                } else {
                    // Still denied, handle the new status (might be DENIED_ALWAYS now)
                    handleStatus(currentPerm, newStatus)
                }
            }
        }
    }

    /**
     * Called when user clicks "Open Settings".
     */
    fun onSettingsConfirmed() {
        resetState()
        onAllGrantedCallback = null
        grantManager.openSettings()
    }

    /**
     * Called when user dismisses any dialog.
     */
    fun onDismiss() {
        resetState()
        onAllGrantedCallback = null
    }

    // --- Internal Logic ---

    private fun handleStatus(grant: GrantPermission, status: GrantStatus): Boolean {
        return when (status) {
            GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED -> true

            GrantStatus.NOT_DETERMINED -> {
                // This can happen if request() returned NOT_DETERMINED
                // (e.g. Motion dummy-query race on iOS, or system dialog dismissed
                // without interaction). Treat as DENIED so we surface rationale next time.
                _state.update {
                    it.copy(
                        isVisible        = true,
                        currentGrant     = grant,
                        showRationale    = true,
                        showSettingsGuide = false,
                        rationaleMessage = currentRationaleMessages[grant]
                    )
                }
                false
            }

            GrantStatus.DENIED -> {
                // Show rationale
                _state.update {
                    it.copy(
                        isVisible        = true,
                        currentGrant     = grant,
                        showRationale    = true,
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
                        isVisible        = true,
                        currentGrant     = grant,
                        showRationale    = false,
                        showSettingsGuide = true,
                        settingsMessage  = currentSettingsMessages[grant]
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
