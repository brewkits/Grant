package dev.brewkits.grant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    scope: CoroutineScope,
    private val savedStateDelegate: SavedStateDelegate = NoOpSavedStateDelegate()
) {
    private companion object {
        // Keys for SavedStateDelegate
        const val KEY_IS_VISIBLE = "grant_handler_is_visible"
        const val KEY_SHOW_RATIONALE = "grant_handler_show_rationale"
        const val KEY_SHOW_SETTINGS = "grant_handler_show_settings"
    }
    private val scope: CoroutineScope

    init {
        // Store the scope (validation happens implicitly when first coroutine is launched)
        this.scope = scope
    }
    private val _state = MutableStateFlow(GrantUiState())

    /**
     * UI state flow that UI layer observes to show dialogs
     */
    val state: StateFlow<GrantUiState> = _state.asStateFlow()

    private val _status = MutableStateFlow(GrantStatus.NOT_DETERMINED)

    /**
     * Current grant status
     */
    val status: StateFlow<GrantStatus> = _status.asStateFlow()

    private var onGrantedCallback: (() -> Unit)? = null

    /**
     * Tracks if we've shown the rationale dialog to the user.
     * This helps us decide whether to show Settings guide immediately.
     *
     * Platform differences:
     * - Android: User goes through rationale flow before permanent denial
     *            If rationale was shown → Help with Settings guide
     * - iOS: No rationale flow (first denial = permanent)
     *        Only show Settings guide on second click
     */
    private var hasShownRationaleDialog = false

    init {
        // Restore state from savedStateDelegate (process death recovery)
        val wasVisible = savedStateDelegate.restoreState(KEY_IS_VISIBLE)?.toBoolean() ?: false
        val wasRationale = savedStateDelegate.restoreState(KEY_SHOW_RATIONALE)?.toBoolean() ?: false
        val wasSettings = savedStateDelegate.restoreState(KEY_SHOW_SETTINGS)?.toBoolean() ?: false

        if (wasVisible) {
            // Directly update _state without saving back to delegate (we're restoring from it)
            _state.update {
                it.copy(
                    isVisible = true,
                    showRationale = wasRationale,
                    showSettingsGuide = wasSettings
                )
            }
        }

        // Initialize status on creation
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
     * Main entry point for requesting grant.
     *
     * This function orchestrates the entire flow:
     * 1. Check current status
     * 2. If NOT_DETERMINED -> Request immediately
     * 3. If DENIED -> Show rationale dialog
     * 4. If DENIED_ALWAYS -> Show settings guide
     * 5. If GRANTED -> Execute callback
     *
     * @param rationaleMessage Custom message for rationale dialog (optional)
     * @param settingsMessage Custom message for settings dialog (optional)
     * @param onGranted Callback that executes ONLY when grant is granted.
     *                  **IMPORTANT**: Callback is cleared after execution to prevent memory leaks.
     *
     * **Example**:
     * ```kotlin
     * handler.request(
     *     rationaleMessage = "We need camera to scan QR codes",
     *     settingsMessage = "Camera is disabled. Enable it in Settings > Grants"
     * ) {
     *     println("Camera is ready!")
     *     openCameraPreview()
     * }
     * ```
     *
     * **Callback Best Practices**:
     * - Callback is automatically cleared after invocation to prevent memory leaks
     * - Safe to capture ViewModel properties (they survive config changes)
     * - Avoid capturing Activity/Fragment directly - use ViewModel events instead
     * - Example (GOOD):
     *   ```kotlin
     *   handler.request { viewModel.navigateToCamera() } // ✅ ViewModel event
     *   ```
     * - Example (BAD):
     *   ```kotlin
     *   handler.request { activity.startCamera() } // ❌ Activity reference
     *   ```
     */
    fun request(
        rationaleMessage: String? = null,
        settingsMessage: String? = null,
        onGranted: () -> Unit
    ) {
        this.onGrantedCallback = onGranted

        scope.launch {
            val currentStatus = grantManager.checkStatus(grant)
            _status.value = currentStatus
            handleStatus(currentStatus, rationaleMessage, settingsMessage)
        }
    }

    /**
     * Called when user confirms rationale dialog and wants to proceed
     */
    fun onRationaleConfirmed() {
        scope.launch {
            // Hide dialog first to prevent user confusion
            resetState()

            val newStatus = grantManager.request(grant)
            _status.value = newStatus

            // Force a refresh after the request to ensure UI is in sync
            refreshStatus()

            // Mark as first request to prevent showing another dialog immediately
            // User just saw system dialog - don't bombard with rationale again if denied
            handleStatus(newStatus, null, null, isFirstRequest = true)
        }
    }

    /**
     * Called when user clicks "Open Settings" button
     */
    fun onSettingsConfirmed() {
        resetState()
        grantManager.openSettings()
    }

    /**
     * Called when user dismisses any dialog (Cancel/Outside tap)
     */
    fun onDismiss() {
        resetState()
        // Clear callback to prevent memory leak when grant flow is cancelled
        onGrantedCallback = null
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
        onGranted: () -> Unit
    ) {
        this.onGrantedCallback = onGranted

        scope.launch {
            val currentStatus = grantManager.checkStatus(grant)
            _status.value = currentStatus
            handleStatusWithCustomUi(
                status = currentStatus,
                rationaleMessage = rationaleMessage,
                settingsMessage = settingsMessage,
                onShowRationale = onShowRationale,
                onShowSettings = onShowSettings
            )
        }
    }

    // --- Internal Logic ---

    private suspend fun handleStatus(
        status: GrantStatus,
        rationaleMessage: String?,
        settingsMessage: String?,
        isFirstRequest: Boolean = false
    ) {
        when (status) {
            GrantStatus.GRANTED -> {
                resetState()
                hasShownRationaleDialog = false  // Reset flag when granted
                // Invoke callback and immediately clear to prevent memory leak
                // (callback may hold reference to Activity/Fragment)
                onGrantedCallback?.invoke()
                onGrantedCallback = null
            }
            GrantStatus.NOT_DETERMINED -> {
                // First time - request immediately
                val result = grantManager.request(grant)

                // Update status BEFORE handling to ensure StateFlow emits
                _status.value = result

                // Prevent infinite loop: only recurse if result changed
                // If request() returns NOT_DETERMINED again, treat as soft denial
                if (result != GrantStatus.NOT_DETERMINED) {
                    // Mark as first request so we don't show dialogs immediately after user denies
                    handleStatus(result, rationaleMessage, settingsMessage, isFirstRequest = true)
                } else {
                    // Unexpected: request returned NOT_DETERMINED (shouldn't happen normally)
                    // Treat as soft denial to prevent infinite recursion
                    handleStatus(GrantStatus.DENIED, rationaleMessage, settingsMessage)
                }
            }
            GrantStatus.DENIED -> {
                // Soft denial - show rationale
                // Skip dialog if this was just denied from first system dialog
                // This prevents bombarding user with 2 dialogs in a row:
                // System dialog → Rationale dialog (bad UX)
                //
                // Only show rationale on subsequent requests when user explicitly
                // clicks the feature button again.
                if (!isFirstRequest) {
                    hasShownRationaleDialog = true  // Mark that we showed rationale
                    updateState {
                        it.copy(
                            isVisible = true,
                            showRationale = true,
                            showSettingsGuide = false,
                            rationaleMessage = rationaleMessage
                        )
                    }
                } else {
                    // Just denied from system dialog - don't show rationale yet
                    // User can try again later and THEN we'll show rationale
                    resetState()
                    onGrantedCallback = null
                }
            }
            GrantStatus.DENIED_ALWAYS -> {
                // Permanent denial - show settings guide
                //
                // Platform differences:
                // - Android: User goes through rationale flow before permanent denial
                //           If rationale shown → They tried to grant → Help with Settings
                // - iOS: First denial = permanent (no soft denial, no rationale)
                //        Don't show Settings after first denial
                //
                // Solution: Check if user saw rationale OR clicked again
                // - hasShownRationaleDialog = true: User went through flow → Show guide
                // - isFirstRequest = false: User clicked again → Show guide
                // - Otherwise: Don't show yet (user just denied for first time)
                val shouldShowSettingsGuide = hasShownRationaleDialog || !isFirstRequest

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
                    // Just denied from system dialog - don't show settings guide yet
                    // User can try again later and THEN we'll show guide
                    // This prevents bad UX: System dialog → Settings dialog (2 dialogs in a row!)
                    resetState()
                    onGrantedCallback = null
                }
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
        _state.update(block)

        // Save to delegate for process death recovery
        val current = _state.value
        savedStateDelegate.saveState(KEY_IS_VISIBLE, current.isVisible.toString())
        savedStateDelegate.saveState(KEY_SHOW_RATIONALE, current.showRationale.toString())
        savedStateDelegate.saveState(KEY_SHOW_SETTINGS, current.showSettingsGuide.toString())
    }

    /**
     * Internal handler for custom UI flow.
     *
     * @param isFirstRequest True if this is the first request from system dialog denial.
     *                       Prevents showing rationale immediately after system dialog.
     */
    private suspend fun handleStatusWithCustomUi(
        status: GrantStatus,
        rationaleMessage: String?,
        settingsMessage: String?,
        onShowRationale: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        onShowSettings: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        isFirstRequest: Boolean = false
    ) {
        when (status) {
            GrantStatus.GRANTED -> {
                // Invoke callback and immediately clear to prevent memory leak
                onGrantedCallback?.invoke()
                onGrantedCallback = null
            }
            GrantStatus.NOT_DETERMINED -> {
                // First time - request immediately
                val result = grantManager.request(grant)
                _status.value = result

                // Prevent infinite loop: only recurse if result changed
                if (result != GrantStatus.NOT_DETERMINED) {
                    handleStatusWithCustomUi(
                        status = result,
                        rationaleMessage = rationaleMessage,
                        settingsMessage = settingsMessage,
                        onShowRationale = onShowRationale,
                        onShowSettings = onShowSettings,
                        isFirstRequest = true  // Mark as first request from system dialog
                    )
                } else {
                    // Treat as soft denial to prevent infinite recursion
                    handleStatusWithCustomUi(
                        status = GrantStatus.DENIED,
                        rationaleMessage = rationaleMessage,
                        settingsMessage = settingsMessage,
                        onShowRationale = onShowRationale,
                        onShowSettings = onShowSettings,
                        isFirstRequest = true
                    )
                }
            }
            GrantStatus.DENIED -> {
                // Add first-request protection to prevent double dialogs
                // If this is the first denial from system dialog, don't show rationale yet
                if (!isFirstRequest) {
                    // Soft denial - invoke rationale callback
                    val message = rationaleMessage ?: "This grant is required for this feature to work."

                    val onConfirm: () -> Unit = {
                        scope.launch {
                            val newStatus = grantManager.request(grant)
                            _status.value = newStatus
                            refreshStatus()
                            handleStatusWithCustomUi(
                                status = newStatus,
                                rationaleMessage = rationaleMessage,
                                settingsMessage = settingsMessage,
                                onShowRationale = onShowRationale,
                                onShowSettings = onShowSettings,
                                isFirstRequest = true  // Mark subsequent requests as first
                            )
                        }
                    }

                    val onDismiss: () -> Unit = {
                        // Clear callback to prevent memory leak
                        onGrantedCallback = null
                    }

                    onShowRationale(message, onConfirm, onDismiss)
                } else {
                    // Just denied from system dialog - don't show rationale yet
                    // User can try again later and THEN we'll show rationale
                    onGrantedCallback = null
                }
            }
            GrantStatus.DENIED_ALWAYS -> {
                // Permanent denial - invoke settings callback
                val message = settingsMessage ?: "Grant denied. Please enable it in Settings."

                val onConfirm: () -> Unit = {
                    grantManager.openSettings()
                    // Clear callback - user needs to return to app and retry
                    onGrantedCallback = null
                }

                val onDismiss: () -> Unit = {
                    // Clear callback to prevent memory leak
                    onGrantedCallback = null
                }

                onShowSettings(message, onConfirm, onDismiss)
            }
        }
    }
}
