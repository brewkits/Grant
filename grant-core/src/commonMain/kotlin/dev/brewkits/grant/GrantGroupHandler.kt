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

/**
 * The UI state produced by [GrantGroupHandler].
 */
data class GrantGroupUiState(
    /**
     * Whether a permission dialog should currently be visible.
     */
    val isVisible: Boolean = false,

    /**
     * The specific permission that is currently being addressed (rationale/settings).
     */
    val currentGrant: GrantPermission? = null,

    /**
     * Whether to show a rationale dialog.
     */
    val showRationale: Boolean = false,

    /**
     * Whether to show a settings guide.
     */
    val showSettingsGuide: Boolean = false,

    /**
     * Custom rationale text for [currentGrant].
     */
    val rationaleMessage: String? = null,

    /**
     * Custom settings guide text for [currentGrant].
     */
    val settingsMessage: String? = null,

    /**
     * The set of permissions in the group that have been successfully granted.
     */
    val grantedGrants: Set<GrantPermission> = emptySet(),

    /**
     * The total number of permissions in the group.
     */
    val totalGrants: Int = 0
)

/**
 * A specialized handler for managing multiple permissions as a single logical group.
 *
 * Use this when a feature requires several unrelated permissions to be granted
 * simultaneously (e.g., an Augmented Reality feature requiring Camera + Location).
 *
 * ### Behavior
 * 1. **Initial Batch**: Requests all denied permissions at once to minimize dialogs.
 * 2. **Sequential Cleanup**: If some are denied, it iterates through them sequentially
 *    to show specific rationale or settings guidance for each.
 * 3. **Success**: The `onAllGranted` callback is only executed when every permission
 *    in the group is either [GrantStatus.GRANTED] or [GrantStatus.PARTIAL_GRANTED].
 *
 * ### Usage Example
 * ```kotlin
 * val arGrants = GrantGroupHandler(
 *     grantManager = grantManager,
 *     grants = listOf(AppGrant.CAMERA, AppGrant.LOCATION),
 *     scope = viewModelScope
 * )
 *
 * arGrants.request(
 *     rationaleMessages = mapOf(
 *         AppGrant.CAMERA to "Camera is needed to render AR content",
 *         AppGrant.LOCATION to "Location is needed to anchor AR objects"
 *     )
 * ) {
 *     // This block runs ONLY when BOTH permissions are granted
 *     launchArFeature()
 * }
 * ```
 */
class GrantGroupHandler(
    private val grantManager: GrantManager,
    private val grants: List<GrantPermission>,
    private val scope: CoroutineScope
) {
    init {
        require(grants.isNotEmpty()) {
            "GrantGroupHandler requires at least one grant."
        }
    }
    private val _state = MutableStateFlow(GrantGroupUiState(totalGrants = grants.size))

    /**
     * Observable state for driving group-based permission UI.
     */
    val state: StateFlow<GrantGroupUiState> = _state.asStateFlow()

    private val _statuses = MutableStateFlow<Map<GrantPermission, GrantStatus>>(emptyMap())

    /**
     * The latest native status for each managed permission in the group.
     */
    val statuses: StateFlow<Map<GrantPermission, GrantStatus>> = _statuses.asStateFlow()

    @kotlin.concurrent.Volatile
    private var onAllGrantedCallback: (() -> Unit)? = null

    @kotlin.concurrent.Volatile
    private var currentRationaleMessages: Map<GrantPermission, String> = emptyMap()

    @kotlin.concurrent.Volatile
    private var currentSettingsMessages: Map<GrantPermission, String> = emptyMap()

    private val requestMutex = Mutex()

    init {
        refreshAllStatuses()
    }

    /**
     * Re-queries the OS for the status of all permissions in the group.
     */
    fun refreshAllStatuses() {
        scope.launch {
            val statusMap = coroutineScope {
                grants.map { grant ->
                    async { grant to grantManager.checkStatus(grant) }
                }.awaitAll().toMap()
            }

            val grantedSet = statusMap.filter { isFullyGranted(it.key, it.value) }.keys

            _statuses.value = statusMap
            _state.update { it.copy(grantedGrants = grantedSet) }
        }
    }

    /**
     * Whether a (grant, status) pair represents a satisfied permission for the
     * purposes of this group.
     *
     * Most permissions accept PARTIAL_GRANTED as a success state, but permissions
     * that require a background upgrade (e.g. LOCATION_ALWAYS) need full
     * background access — PARTIAL_GRANTED for those grants means foreground
     * was granted but background was denied, so the user still needs to be
     * directed to settings.
     */
    private fun isFullyGranted(grant: GrantPermission, status: GrantStatus): Boolean =
        when (status) {
            GrantStatus.GRANTED -> true
            GrantStatus.PARTIAL_GRANTED -> !grant.requiresBackgroundUpgrade
            else -> false
        }

    /**
     * Initiates the group permission request flow.
     *
     * @param rationaleMessages Map of permission to its custom rationale text.
     * @param settingsMessages Map of permission to its custom settings guide text.
     * @param onAllGranted Callback executed ONLY when all permissions are granted.
     */
    fun request(
        rationaleMessages: Map<GrantPermission, String> = emptyMap(),
        settingsMessages: Map<GrantPermission, String> = emptyMap(),
        onAllGranted: () -> Unit
    ) {
        scope.launch {
            if (!requestMutex.tryLock()) return@launch

            this@GrantGroupHandler.onAllGrantedCallback = onAllGranted
            this@GrantGroupHandler.currentRationaleMessages = rationaleMessages
            this@GrantGroupHandler.currentSettingsMessages = settingsMessages

            try {
                val currentStatuses = coroutineScope {
                    grants.map { grant ->
                        async { grant to grantManager.checkStatus(grant) }
                    }.awaitAll().toMap()
                }

                val deniedGrants = currentStatuses
                    .filter { !isFullyGranted(it.key, it.value) }
                    .keys.toList()

                _statuses.update { it + currentStatuses }

                val initiallyGranted = currentStatuses
                    .filter { isFullyGranted(it.key, it.value) }
                    .keys

                if (deniedGrants.isEmpty()) {
                    resetState()
                    _state.update { it.copy(grantedGrants = initiallyGranted) }
                    val callback = onAllGrantedCallback
                    onAllGrantedCallback = null
                    callback?.invoke()
                    return@launch
                }

                val results = grantManager.request(deniedGrants)
                _statuses.update { it + results }

                val newlyGranted = results.filter { isFullyGranted(it.key, it.value) }.keys
                _state.update { it.copy(grantedGrants = it.grantedGrants + newlyGranted) }

                val stillDenied = results.filter { !isFullyGranted(it.key, it.value) }

                if (stillDenied.isEmpty()) {
                    resetState()
                    val callback = onAllGrantedCallback
                    onAllGrantedCallback = null
                    callback?.invoke()
                } else {
                    val firstDenied = stillDenied.keys.first()
                    handleStatus(firstDenied, stillDenied[firstDenied]!!)
                }
            } finally {
                requestMutex.unlock()
            }
        }
    }

    /**
     * Confirms the rationale for the [GrantGroupUiState.currentGrant] and requests
     * that specific permission again.
     */
    fun onRationaleConfirmed() {
        scope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                val currentPerm = _state.value.currentGrant ?: return@launch
                val newStatus = grantManager.request(currentPerm)
                _statuses.update { it + (currentPerm to newStatus) }

                if (isFullyGranted(currentPerm, newStatus)) {
                    val currentStatuses = coroutineScope {
                        grants.map { g ->
                            async { g to grantManager.checkStatus(g) }
                        }.awaitAll().toMap()
                    }
                    _statuses.update { currentStatuses }

                    val grantedSet = currentStatuses
                        .filter { isFullyGranted(it.key, it.value) }
                        .keys

                    _state.update { it.copy(grantedGrants = grantedSet) }

                    val stillDenied = currentStatuses
                        .filter { !isFullyGranted(it.key, it.value) }

                    if (stillDenied.isEmpty()) {
                        resetState()
                        val callback = onAllGrantedCallback
                        onAllGrantedCallback = null
                        callback?.invoke()
                    } else {
                        val nextDenied = stillDenied.keys.first()
                        handleStatus(nextDenied, stillDenied[nextDenied]!!)
                    }
                } else {
                    handleStatus(currentPerm, newStatus)
                }
            } finally {
                requestMutex.unlock()
            }
        }
    }

    /**
     * Confirms the settings guide and opens the app's settings page.
     */
    fun onSettingsConfirmed() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            onAllGrantedCallback = null
            grantManager.openSettings()
        } finally {
            requestMutex.unlock()
        }
    }

    /**
     * Dismisses the current dialog and cancels the group permission request flow.
     */
    fun onDismiss() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            onAllGrantedCallback = null
        } finally {
            requestMutex.unlock()
        }
    }

    private fun handleStatus(grant: GrantPermission, status: GrantStatus): Boolean {
        return when (status) {
            GrantStatus.GRANTED -> true

            GrantStatus.PARTIAL_GRANTED -> {
                if (!grant.requiresBackgroundUpgrade) {
                    true
                } else {
                    // Foreground granted but background denied; route the user
                    // to settings, consistent with single-permission handling.
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

            GrantStatus.NOT_DETERMINED, GrantStatus.DENIED -> {
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
