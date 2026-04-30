package dev.brewkits.grant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * UI State for grant dialogs.
 *
 * This state drives the UI layer to show appropriate dialogs:
 * - Rationale dialog when grant was denied softly
 * - Settings guide dialog when grant is permanently denied
 */
data class GrantUiState(
    /**
     * Whether any grant dialog should be visible
     */
    val isVisible: Boolean = false,

    /**
     * Show rationale dialog (when user denied once, explain why we need it)
     */
    val showRationale: Boolean = false,

    /**
     * Show settings guide (when permanently denied, must go to Settings)
     */
    val showSettingsGuide: Boolean = false,

    /**
     * Optional custom message for rationale
     */
    val rationaleMessage: String? = null,

    /**
     * Optional custom message for settings guide
     */
    val settingsMessage: String? = null
)

/**
 * Encapsulates ALL grant logic in a clean, reusable component.
 *
 * This class follows the **Composition Pattern** - ViewModels compose this handler
 * instead of implementing grant logic themselves.
 *
 * **Benefits**:
 * 1. **DRY**: Write grant logic once, reuse everywhere
 * 2. **Clean ViewModels**: Reduce from 30+ lines to 3 lines
 * 3. **Consistent UX**: All features follow same grant flow
 * 4. **Testable**: Easy to mock and test
 * 5. **Process Death Recovery**: Optional state restoration on Android
 *
 * **Usage in ViewModel**:
 * ```kotlin
 * class CameraViewModel(grantManager: GrantManager) : ViewModel() {
 *     val cameraGrant = GrantHandler(
 *         grantManager = grantManager,
 *         grant = AppGrant.CAMERA,  // Or use RawPermission for custom permissions
 *         scope = viewModelScope // CRITICAL: Use viewModelScope!
 *     )
 *
 *     fun onCaptureClick() {
 *         cameraGrant.request {
 *             // Only runs when grant is GRANTED
 *             openCamera()
 *         }
 *     }
 * }
 * ```
 *
 * **Usage in UI (Compose)**:
 * ```kotlin
 * GrantDialogHandler(handler = viewModel.cameraGrant)
 * ```
 *
 * **Process Death Recovery (Android)**:
 * ```kotlin
 * class CameraViewModel(
 *     grantManager: GrantManager,
 *     savedStateHandle: SavedStateHandle
 * ) : ViewModel() {
 *     val cameraGrant = GrantHandler(
 *         grantManager = grantManager,
 *         grant = AppGrant.CAMERA,
 *         scope = viewModelScope,
 *         savedStateDelegate = AndroidSavedStateDelegate(savedStateHandle) // Optional
 *     )
 * }
 * ```
 *
 * @param grantManager The underlying grant manager
 * @param grant The specific grant this handler manages (AppGrant or RawPermission)
 * @param scope CoroutineScope for launching grant requests.
 *
 *              **CRITICAL - Scope Requirements:**
 *              - **MUST** use [viewModelScope] from a ViewModel
 *              - Survives configuration changes (screen rotation)
 *              - Automatically cancelled when ViewModel is cleared
 *              - Prevents memory leaks
 *
 *              **DO NOT USE:**
 *              - [GlobalScope]: Never cancelled, causes memory leaks
 *              - [lifecycleScope]: Cancelled on config changes, breaks ongoing requests
 *              - Custom scopes with short lifecycle
 *              - Any scope that doesn't outlive the grant flow
 *
 *              **Example of CORRECT usage:**
 *              ```kotlin
 *              class MyViewModel : ViewModel() {
 *                  val handler = GrantHandler(..., scope = viewModelScope) // ✅ Correct
 *              }
 *              ```
 *
 *              **Example of WRONG usage:**
 *              ```kotlin
 *              class MyFragment : Fragment() {
 *                  val handler = GrantHandler(..., scope = lifecycleScope) // ❌ Wrong!
 *                  // This breaks on screen rotation!
 *              }
 *              ```
 * @param savedStateDelegate Optional delegate for saving/restoring state across process death.
 *                           Use AndroidSavedStateDelegate on Android for automatic restoration.
 *                           Defaults to NoOpSavedStateDelegate (no persistence).
 */
class GrantHandler(
    private val grantManager: GrantManager,
    private val grant: GrantPermission,
    private val scope: CoroutineScope,
    private val savedStateDelegate: SavedStateDelegate = NoOpSavedStateDelegate()
) {
    private companion object {
        const val KEY_IS_VISIBLE     = "grant_handler_is_visible"
        const val KEY_SHOW_RATIONALE = "grant_handler_show_rationale"
        const val KEY_SHOW_SETTINGS  = "grant_handler_show_settings"
    }

    private val _state  = MutableStateFlow(GrantUiState())
    val state: StateFlow<GrantUiState> = _state.asStateFlow()

    private val _status = MutableStateFlow(GrantStatus.NOT_DETERMINED)
    val status: StateFlow<GrantStatus> = _status.asStateFlow()

    private val _grantedEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val grantedEvents: kotlinx.coroutines.flow.SharedFlow<Unit> = _grantedEvents.asSharedFlow()

    @kotlin.concurrent.Volatile
    private var onGrantedCallback: ((GrantStatus) -> Unit)? = null

    @kotlin.concurrent.Volatile
    private var hasShownRationaleDialog = false

    private val requestMutex = Mutex()

    init {
        // Restore visible dialog state from savedStateDelegate (process death recovery)
        val wasVisible   = savedStateDelegate.restoreState(KEY_IS_VISIBLE)?.toBoolean() ?: false
        val wasRationale = savedStateDelegate.restoreState(KEY_SHOW_RATIONALE)?.toBoolean() ?: false
        val wasSettings  = savedStateDelegate.restoreState(KEY_SHOW_SETTINGS)?.toBoolean() ?: false
        if (wasVisible) {
            _state.update {
                it.copy(
                    isVisible        = true,
                    showRationale    = wasRationale,
                    showSettingsGuide = wasSettings
                )
            }
        }
        // Initialize status on creation (non-blocking background check)
        refreshStatus()
    }

    /**
     * Refresh the current grant status.
     * Call this after user returns from Settings.
     */
    fun refreshStatus() {
        scope.launch {
            _status.value = grantManager.checkStatus(grant)
        }
    }

    /**
     * If a request is already in-flight, the call is silently ignored — no new coroutine
     * is launched and the in-flight request continues to completion.
     *
     * This function orchestrates the entire flow:
     * 1. Check current status
     * 2. If NOT_DETERMINED → Request immediately
     * 3. If DENIED → Show rationale dialog
     * 4. If DENIED_ALWAYS → Show settings guide
     * 5. If GRANTED → Execute callback
     *
     * @param rationaleMessage Custom message for rationale dialog (optional)
     * @param settingsMessage  Custom message for settings dialog (optional)
     * @param onGranted        Callback executed ONLY when grant is GRANTED or PARTIAL_GRANTED.
     *                         Callback is cleared after execution to prevent memory leaks.
     */
    fun request(
        rationaleMessage: String? = null,
        settingsMessage: String? = null,
        onGranted: (GrantStatus) -> Unit
    ) {
        // tryLock() is atomic — safe concurrent guard replacing the racy isLocked check.
        if (!requestMutex.tryLock()) return
        
        this.onGrantedCallback = onGranted

        val job = scope.launch {
            try {
                val currentStatus = grantManager.checkStatus(grant)
                _status.value = currentStatus
                handleStatus(currentStatus, rationaleMessage, settingsMessage)
            } catch (e: Exception) {
                // If an unexpected error occurs, clear the callback to prevent hanging references
                onGrantedCallback = null
                throw e 
            } finally {
                requestMutex.unlock()
            }
        }
        
        // Safety: If the scope was already cancelled, launch() returns a cancelled job 
        // and the block never runs. We must unlock manually to prevent a permanent leak.
        if (!job.isActive) {
            requestMutex.unlock()
        }
    }

    /**
     * Called when user confirms rationale dialog and wants to proceed.
     */
    fun onRationaleConfirmed() {
        if (!requestMutex.tryLock()) return

        val job = scope.launch {
            try {
                // Hide dialog first to prevent user confusion
                resetState()

                val newStatus = grantManager.request(grant)
                _status.value = newStatus

                // Force a refresh after the request to ensure UI is in sync
                _status.value = grantManager.checkStatus(grant)

                // Mark as first request to prevent showing another dialog immediately
                // User just saw system dialog - don't bombard with rationale again if denied
                handleStatus(newStatus, null, null, isFirstRequest = true)
            } finally {
                requestMutex.unlock()
            }
        }

        if (!job.isActive) {
            requestMutex.unlock()
        }
    }

    /**
     * Called when user clicks "Open Settings" button
     */
    fun onSettingsConfirmed() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            grantManager.openSettings()
            onGrantedCallback = null
        } finally {
            requestMutex.unlock()
        }
    }

    /**
     * Called when user dismisses any dialog (Cancel/Outside tap)
     */
    fun onDismiss() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            // Clear callback to prevent memory leak when grant flow is cancelled
            onGrantedCallback = null
        } finally {
            requestMutex.unlock()
        }
    }

    /**
     * Request grant with custom UI callback handlers.
     *
     * This method provides an alternative to the state-based approach, allowing
     * you to inject custom UI directly into the grant flow. Instead of
     * observing state and showing dialogs, you provide callback functions that
     * will be invoked when rationale or settings guidance is needed.
     *
     * **Use this when:**
     * - You want full control over the UI (custom dialogs, bottom sheets, etc.)
     * - You're using a non-Compose UI framework
     * - You want to integrate with existing dialog systems
     * - You prefer imperative UI over declarative state
     *
     * **Use the standard `request()` method when:**
     * - Using Compose with GrantDialogHandler
     * - Following the recommended state-based approach
     * - You want consistent dialogs across the app
     *
     * @param rationaleMessage Default message to show in rationale UI
     * @param settingsMessage Default message to show in settings UI
     * @param onShowRationale Called when rationale should be shown.
     *                        Parameters:
     *                        - message: The rationale message
     *                        - onConfirm: Call this when user confirms (will trigger grant request)
     *                        - onDismiss: Call this when user dismisses (will cancel flow)
     * @param onShowSettings Called when settings guidance should be shown.
     *                       Parameters:
     *                       - message: The settings message
     *                       - onConfirm: Call this when user confirms (will open Settings)
     *                       - onDismiss: Call this when user dismisses (will cancel flow)
     * @param onGranted Callback that executes ONLY when grant is granted
     *
     * **Example (Custom Material Dialog)**:
     * ```kotlin
     * handler.requestWithCustomUi(
     *     rationaleMessage = "Camera is needed to scan QR codes",
     *     settingsMessage = "Please enable camera in Settings",
     *     onShowRationale = { message, onConfirm, onDismiss ->
     *         MaterialAlertDialogBuilder(context)
     *             .setMessage(message)
     *             .setPositiveButton("Continue") { _, _ -> onConfirm() }
     *             .setNegativeButton("Cancel") { _, _ -> onDismiss() }
     *             .show()
     *     },
     *     onShowSettings = { message, onConfirm, onDismiss ->
     *         MaterialAlertDialogBuilder(context)
     *             .setMessage(message)
     *             .setPositiveButton("Open Settings") { _, _ -> onConfirm() }
     *             .setNegativeButton("Cancel") { _, _ -> onDismiss() }
     *             .show()
     *     }
     * ) {
     *     // Grant granted
     *     openCamera()
     * }
     * ```
     *
     * **Example (Bottom Sheet)**:
     * ```kotlin
     * handler.requestWithCustomUi(
     *     onShowRationale = { message, onConfirm, onDismiss ->
     *         showBottomSheet {
     *             RationaleBottomSheet(
     *                 message = message,
     *                 onAccept = onConfirm,
     *                 onCancel = onDismiss
     *             )
     *         }
     *     },
     *     // ...
     * )
     * ```
     */
    fun requestWithCustomUi(
        rationaleMessage: String? = null,
        settingsMessage: String? = null,
        onShowRationale: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        onShowSettings: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        onGranted: (GrantStatus) -> Unit
    ) {
        if (!requestMutex.tryLock()) return
        this.onGrantedCallback = onGranted

        val job = scope.launch {
            try {
                val currentStatus = grantManager.checkStatus(grant)
                _status.value = currentStatus
                handleStatusWithCustomUi(
                    status = currentStatus,
                    rationaleMessage = rationaleMessage,
                    settingsMessage = settingsMessage,
                    onShowRationale = onShowRationale,
                    onShowSettings = onShowSettings
                )
            } finally {
                onGrantedCallback = null
                requestMutex.unlock()
            }
        }

        if (!job.isActive) {
            requestMutex.unlock()
        }
    }

    // --- Internal Logic ---

    
    private sealed class FlowState {
        data class Check(val isFirstRequest: Boolean = false) : FlowState()
        data class HandleResult(val status: GrantStatus, val isFirstRequest: Boolean = false) : FlowState()
        object Done : FlowState()
    }

    private suspend fun handleStatus(
        status: GrantStatus,
        rationaleMessage: String?,
        settingsMessage: String?,
        isFirstRequest: Boolean = false
    ) {
        var currentState: FlowState = FlowState.HandleResult(status, isFirstRequest)

        while (currentState !is FlowState.Done) {
            when (val state = currentState) {
                is FlowState.Check -> {
                    val result = grantManager.request(grant)
                    _status.value = result
                    
                    if (result != GrantStatus.NOT_DETERMINED) {
                        currentState = FlowState.HandleResult(result, isFirstRequest = true)
                    } else {
                        currentState = FlowState.HandleResult(GrantStatus.DENIED, isFirstRequest = state.isFirstRequest)
                    }
                }
                is FlowState.HandleResult -> {
                    when (state.status) {
                        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED -> {
                            resetState()
                            hasShownRationaleDialog = false
                            _grantedEvents.tryEmit(Unit)
                            onGrantedCallback?.invoke(state.status)
                            onGrantedCallback = null
                            currentState = FlowState.Done
                        }
                        GrantStatus.NOT_DETERMINED -> {
                            currentState = FlowState.Check(isFirstRequest = state.isFirstRequest)
                        }
                        GrantStatus.DENIED -> {
                            if (!PlatformConfig.isRationaleSupported) {
                                currentState = FlowState.HandleResult(GrantStatus.DENIED_ALWAYS, state.isFirstRequest)
                            } else {
                                if (!state.isFirstRequest) {
                                    hasShownRationaleDialog = true
                                    updateState {
                                        it.copy(
                                            isVisible = true,
                                            showRationale = true,
                                            showSettingsGuide = false,
                                            rationaleMessage = rationaleMessage
                                        )
                                    }
                                } else {
                                    resetState()
                                    onGrantedCallback = null
                                }
                                currentState = FlowState.Done
                            }
                        }
                        GrantStatus.DENIED_ALWAYS -> {
                            val shouldShowSettingsGuide = hasShownRationaleDialog || !state.isFirstRequest
                            if (shouldShowSettingsGuide) {
                                updateState {
                                    it.copy(
                                        isVisible = true,
                                        showRationale = false,
                                        showSettingsGuide = true,
                                        settingsMessage = settingsMessage
                                    )
                                }
                            } else {
                                resetState()
                                onGrantedCallback = null
                            }
                            currentState = FlowState.Done
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun resetState() {
        updateState { GrantUiState() }
    }

    /**
     * Updates the state and persists it to savedStateDelegate.
     * This ensures state is restored after process death.
     */
    private fun updateState(block: (GrantUiState) -> GrantUiState) {
        val oldState = _state.value
        _state.update(block)
        val newState = _state.value

        // Optimization: Only save to delegate if visible state changed
        // We only care about persisting visibility and dialog types for process death
        if (oldState.isVisible != newState.isVisible ||
            oldState.showRationale != newState.showRationale ||
            oldState.showSettingsGuide != newState.showSettingsGuide
        ) {
            savedStateDelegate.saveState(KEY_IS_VISIBLE, newState.isVisible.toString())
            savedStateDelegate.saveState(KEY_SHOW_RATIONALE, newState.showRationale.toString())
            savedStateDelegate.saveState(KEY_SHOW_SETTINGS, newState.showSettingsGuide.toString())
        }
    }

    private suspend fun handleStatusWithCustomUi(
        status: GrantStatus,
        rationaleMessage: String?,
        settingsMessage: String?,
        onShowRationale: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        onShowSettings: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        isFirstRequest: Boolean = false
    ) {
        var currentState: FlowState = FlowState.HandleResult(status, isFirstRequest)

        while (currentState !is FlowState.Done) {
            when (val state = currentState) {
                is FlowState.Check -> {
                    val result = grantManager.request(grant)
                    _status.value = result
                    
                    if (result != GrantStatus.NOT_DETERMINED) {
                        currentState = FlowState.HandleResult(result, isFirstRequest = true)
                    } else {
                        currentState = FlowState.HandleResult(GrantStatus.DENIED, isFirstRequest = state.isFirstRequest)
                    }
                }
                is FlowState.HandleResult -> {
                    when (state.status) {
                        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED -> {
                            _grantedEvents.tryEmit(Unit)
                            onGrantedCallback?.invoke(state.status)
                            onGrantedCallback = null
                            currentState = FlowState.Done
                        }
                        GrantStatus.NOT_DETERMINED -> {
                            currentState = FlowState.Check(isFirstRequest = state.isFirstRequest)
                        }
                        GrantStatus.DENIED -> {
                            if (!state.isFirstRequest) {
                                val message = rationaleMessage ?: "This grant is required for this feature to work."
                                val confirmed = suspendCancellableCoroutine<Boolean> { cont ->
                                    val onConfirm: () -> Unit = { if (cont.isActive) cont.resume(true) }
                                    val onDismiss: () -> Unit = { if (cont.isActive) cont.resume(false) }
                                    onShowRationale(message, onConfirm, onDismiss)
                                }
                                
                                if (confirmed) {
                                    val newStatus = grantManager.request(grant)
                                    _status.value = newStatus
                                    refreshStatus()
                                    currentState = FlowState.HandleResult(newStatus, isFirstRequest = true)
                                } else {
                                    onGrantedCallback = null
                                    currentState = FlowState.Done
                                }
                            } else {
                                onGrantedCallback = null
                                currentState = FlowState.Done
                            }
                        }
                        GrantStatus.DENIED_ALWAYS -> {
                            val message = settingsMessage ?: "Grant denied. Please enable it in Settings."
                            val confirmed = suspendCancellableCoroutine<Boolean> { cont ->
                                val onConfirm: () -> Unit = { if (cont.isActive) cont.resume(true) }
                                val onDismiss: () -> Unit = { if (cont.isActive) cont.resume(false) }
                                onShowSettings(message, onConfirm, onDismiss)
                            }

                            if (confirmed) {
                                grantManager.openSettings()
                            }
                            onGrantedCallback = null
                            currentState = FlowState.Done
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
