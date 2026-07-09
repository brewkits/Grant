package dev.brewkits.grant

import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
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
    private val savedStateDelegate: SavedStateDelegate = NoOpSavedStateDelegate(),
    private val eventListener: GrantEventListener? = null,
) {
    private companion object {
        const val KEY_IS_VISIBLE     = "grant_handler_is_visible"
        const val KEY_SHOW_RATIONALE = "grant_handler_show_rationale"
        const val KEY_SHOW_SETTINGS  = "grant_handler_show_settings"
        const val KEY_RATIONALE_MSG  = "grant_handler_rationale_msg"
        const val KEY_SETTINGS_MSG   = "grant_handler_settings_msg"
        
        const val TAG = "GrantHandler"
    }

    private val _state  = MutableStateFlow(GrantUiState())
    val state: StateFlow<GrantUiState> = _state.asStateFlow()

    private val _status = MutableStateFlow(GrantStatus.NOT_DETERMINED)
    val status: StateFlow<GrantStatus> = _status.asStateFlow()

    private val _grantedEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val grantedEvents: kotlinx.coroutines.flow.SharedFlow<Unit> = _grantedEvents.asSharedFlow()

    @kotlin.concurrent.Volatile
    private var onGrantedCallback: ((GrantStatus) -> Unit)? = null

    @kotlin.concurrent.Volatile
    private var onResultCallback: ((GrantStatus) -> Unit)? = null

    private fun clearCallbacks(finalStatus: GrantStatus? = null) {
        val statusToReport = finalStatus ?: _status.value
        onGrantedCallback = null
        onResultCallback?.invoke(statusToReport)
        onResultCallback = null
    }

    @kotlin.concurrent.Volatile
    private var hasShownRationaleDialog = false

    private val requestMutex = Mutex()

    init {
        // Restore visible dialog state from savedStateDelegate (process death recovery)
        val wasVisible   = savedStateDelegate.restoreState(KEY_IS_VISIBLE)?.toBoolean() ?: false
        val wasRationale = savedStateDelegate.restoreState(KEY_SHOW_RATIONALE)?.toBoolean() ?: false
        val wasSettings  = savedStateDelegate.restoreState(KEY_SHOW_SETTINGS)?.toBoolean() ?: false
        val restoredRationaleMsg = savedStateDelegate.restoreState(KEY_RATIONALE_MSG)
        val restoredSettingsMsg  = savedStateDelegate.restoreState(KEY_SETTINGS_MSG)
        
        if (wasVisible) {
            _state.update {
                it.copy(
                    isVisible        = true,
                    showRationale    = wasRationale,
                    showSettingsGuide = wasSettings,
                    rationaleMessage = restoredRationaleMsg,
                    settingsMessage  = restoredSettingsMsg
                )
            }
        }
        
        // Initialize status and handle auto-resume after process death
        scope.launch {
            val currentStatus = grantManager.checkStatus(grant)
            _status.value = currentStatus
            
            // IF we were visible before process death AND now we are satisfied,
            // it means the user likely granted it and then process death occurred,
            // or they granted it in Settings. We should trigger the success event.
            // LOCATION_ALWAYS PARTIAL_GRANTED is NOT satisfied (background still missing).
            if (wasVisible && isSatisfied(currentStatus)) {
                resetState()
                _grantedEvents.tryEmit(Unit)
            }
        }
    }

    /**
     * Whether [status] fully satisfies this grant.
     *
     * Most permissions accept [GrantStatus.PARTIAL_GRANTED] as a success state, but
     * grants that require a background upgrade (e.g. LOCATION_ALWAYS) need full
     * background access — for those, PARTIAL_GRANTED means foreground was granted
     * but background was denied, so the user must still be routed to Settings and it
     * is NOT treated as satisfied. Mirrors the [handleStatus] PARTIAL_GRANTED branch
     * and GrantGroupHandler.isFullyGranted.
     */
    private fun isSatisfied(status: GrantStatus): Boolean = when (status) {
        GrantStatus.GRANTED -> true
        GrantStatus.PARTIAL_GRANTED -> !grant.requiresBackgroundUpgrade
        else -> false
    }

    /**
     * Refresh the current grant status.
     * Call this after user returns from Settings.
     */
    fun refreshStatus() {
        scope.launch {
            val newStatus = grantManager.checkStatus(grant)
            val oldStatus = _status.value
            _status.value = newStatus

            // Auto-complete flow if status became satisfied while a dialog was showing.
            // For LOCATION_ALWAYS, a PARTIAL_GRANTED result is NOT satisfied — the
            // settings guide must remain so the user can grant background access.
            if (state.value.isVisible && isSatisfied(newStatus)) {
                resetState()
                // The flow is complete (user granted, likely via Settings). Reset the
                // rationale memory so a later fresh attempt explains the rationale again,
                // mirroring the GRANTED branch of handleStatus().
                hasShownRationaleDialog = false
                _grantedEvents.tryEmit(Unit)
                eventListener?.onGranted(grant, newStatus)
                onGrantedCallback?.invoke(newStatus)
                clearCallbacks(newStatus)
            }
        }
    }

    /**
     * Call this when the app returns to foreground to check if user changed settings.
     */
    fun onReturnFromSettings() {
        refreshStatus()
    }

    /**
     * If a request is already in-flight, the call is logged and ignored.
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
        scope.launch {
            if (!requestMutex.tryLock()) {
                GrantLogger.d(TAG, "Request for ${grant.identifier} is already in progress. Ignoring duplicate call.")
                return@launch
            }
            try {
                this@GrantHandler.onGrantedCallback = onGranted
                val currentStatus = grantManager.checkStatus(grant)
                _status.value = currentStatus
                eventListener?.onRequested(grant, currentStatus)
                handleStatus(currentStatus, UiStrategy.StateBased(rationaleMessage, settingsMessage))
            } catch (e: CancellationException) {
                clearCallbacks()
                throw e
            } catch (e: Exception) {
                GrantLogger.e(TAG, "Error during grant request for ${grant.identifier}", e)
                clearCallbacks()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    /**
     * A suspending alternative to [request].
     * Suspends the current coroutine until the grant flow completes (granted, denied, or dismissed).
     *
     * When the flow needs a rationale or settings-guide dialog, it parks until the dialog host
     * resumes it — so a host SHOULD be collecting [state] (e.g. `GrantDialog`) before
     * requesting. With no host attached the flow does not park: the dialog state is cleared and
     * the call completes immediately with the denied status (2.2.4 — previously it suspended
     * forever, silently wedging the caller). The callback-based [request] is unaffected.
     *
     * @param rationaleMessage Custom message for rationale dialog (optional)
     * @param settingsMessage  Custom message for settings dialog (optional)
     * @return The final [GrantStatus] after the flow completes. Returns [GrantStatus.BUSY]
     *         if a request is already in progress.
     */
    suspend fun requestSuspend(
        rationaleMessage: String? = null,
        settingsMessage: String? = null
    ): GrantStatus = suspendCancellableCoroutine { cont ->
        val job = scope.launch {
            if (!requestMutex.tryLock()) {
                GrantLogger.d(TAG, "Suspending request for ${grant.identifier} is already in progress.")
                if (cont.isActive) cont.resume(GrantStatus.BUSY)
                return@launch
            }
            try {
                this@GrantHandler.onResultCallback = { finalStatus ->
                    if (cont.isActive) cont.resume(finalStatus)
                }
                val currentStatus = grantManager.checkStatus(grant)
                _status.value = currentStatus
                eventListener?.onRequested(grant, currentStatus)
                handleStatus(currentStatus, UiStrategy.StateBased(rationaleMessage, settingsMessage))
                // 2.2.4 no-host safeguard: handleStatus may have parked the flow on a rationale /
                // settings-guide dialog, to be resumed by the dialog host. If NO host is
                // collecting [state], nothing can ever resume it and this suspend would hang
                // FOREVER (real-world case: a headless requestSuspend on a grant whose dialogs
                // the app never renders — the Lam gallery skeleton-hang, 2026-07-09). Detect
                // "parked with no host", clear the unrenderable dialog state (mirroring
                // onDismiss, including the rationale memory — the user never saw a dialog),
                // and complete with the current status.
                if (cont.isActive && _state.value.isVisible && !hasStateHost) {
                    resetState()
                    hasShownRationaleDialog = false
                    clearCallbacks()
                }
            } catch (e: CancellationException) {
                clearCallbacks()
                throw e
            } catch (e: Exception) {
                GrantLogger.e(TAG, "Error during suspending grant request for ${grant.identifier}", e)
                clearCallbacks()
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            } finally {
                requestMutex.unlock()
            }
        }

        cont.invokeOnCancellation {
            job.cancel()
        }
    }

    /**
     * A Flow-based alternative to [request].
     * Returns a [Flow] that executes the permission request when collected.
     * 
     * @param rationaleMessage Custom message for rationale dialog (optional)
     * @param settingsMessage  Custom message for settings dialog (optional)
     * @return A Flow emitting the final [GrantStatus].
     */
    fun requestFlow(
        rationaleMessage: String? = null,
        settingsMessage: String? = null
    ): Flow<GrantStatus> = flow {
        emit(requestSuspend(rationaleMessage, settingsMessage))
    }

    /**
     * Called when user confirms rationale dialog and wants to proceed.
     */
    fun onRationaleConfirmed() {
        scope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                val currentRationaleMsg = state.value.rationaleMessage
                val currentSettingsMsg = state.value.settingsMessage
                resetState()
                val newStatus = grantManager.request(grant)
                _status.value = newStatus
                handleStatus(newStatus, UiStrategy.StateBased(currentRationaleMsg, currentSettingsMsg), isFirstRequest = true)
            } catch (e: Exception) {
                clearCallbacks()
            } finally {
                requestMutex.unlock()
            }
        }
    }

    /**
     * Called when user clicks "Open Settings" button
     */
    fun onSettingsConfirmed() {
        scope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                resetState()
                eventListener?.onSettingsOpened(grant)
                grantManager.openSettings()
                // Do NOT clear callbacks here. We wait for user to return from settings.
                // refreshStatus() or onReturnFromSettings() will handle completion.
            } finally {
                requestMutex.unlock()
            }
        }
    }

    /**
     * Called when user dismisses any dialog (Cancel/Outside tap)
     */
    fun onDismiss() {
        scope.launch {
            if (!requestMutex.tryLock()) return@launch
            try {
                hasShownRationaleDialog = false
                resetState()
                clearCallbacks()
            } finally {
                requestMutex.unlock()
            }
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
        scope.launch {
            if (!requestMutex.tryLock()) return@launch
            this@GrantHandler.onGrantedCallback = onGranted
            try {
                val currentStatus = grantManager.checkStatus(grant)
                _status.value = currentStatus
                eventListener?.onRequested(grant, currentStatus)
                handleStatus(
                    status = currentStatus,
                    ui = UiStrategy.CustomUi(rationaleMessage, settingsMessage, onShowRationale, onShowSettings),
                )
            } finally {
                clearCallbacks()
                requestMutex.unlock()
            }
        }
    }

    /**
     * Describes how the flow surfaces UI to the user.
     *
     * [StateBased] updates [GrantUiState] and exits the state machine — the Compose
     * layer observes the state and the flow resumes via [onRationaleConfirmed] /
     * [onSettingsConfirmed]. [CustomUi] invokes caller-supplied callbacks and suspends
     * inside the same coroutine until the user responds.
     */
    private sealed class UiStrategy {
        abstract val rationaleMessage: String?
        abstract val settingsMessage: String?

        data class StateBased(
            override val rationaleMessage: String?,
            override val settingsMessage: String?,
        ) : UiStrategy()

        data class CustomUi(
            override val rationaleMessage: String?,
            override val settingsMessage: String?,
            val onShowRationale: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
            val onShowSettings: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
        ) : UiStrategy()
    }

    private sealed class FlowState {
        data class Check(val isFirstRequest: Boolean = false) : FlowState()
        data class HandleResult(val status: GrantStatus, val isFirstRequest: Boolean = false) : FlowState()
        object Done : FlowState()
    }

    /**
     * Unified state machine for all request paths.
     *
     * The only behavioural difference between [UiStrategy.StateBased] and
     * [UiStrategy.CustomUi] is how rationale and settings dialogs are presented:
     *
     * - **StateBased**: emits [GrantUiState] changes and sets [FlowState.Done]; the
     *   Compose layer shows the dialog and restarts the flow via [onRationaleConfirmed]
     *   or [onSettingsConfirmed].
     * - **CustomUi**: invokes the caller-supplied lambda and suspends until the user
     *   responds within the same coroutine; the flow never exits mid-dialog.
     *
     * All other transitions (GRANTED, PARTIAL_GRANTED, NOT_DETERMINED, BUSY, the
     * [hasShownRationaleDialog] guard, and all [GrantEventListener] emissions) are
     * identical for both strategies.
     */
    private suspend fun handleStatus(
        status: GrantStatus,
        ui: UiStrategy,
        isFirstRequest: Boolean = false,
    ) {
        var currentState: FlowState = FlowState.HandleResult(status, isFirstRequest)

        while (currentState !is FlowState.Done) {
            when (val state = currentState) {
                is FlowState.Check -> {
                    val result = grantManager.request(grant)
                    _status.value = result
                    currentState = if (result != GrantStatus.NOT_DETERMINED)
                        FlowState.HandleResult(result, isFirstRequest = true)
                    else
                        FlowState.HandleResult(GrantStatus.DENIED, isFirstRequest = state.isFirstRequest)
                }

                is FlowState.HandleResult -> {
                    when (state.status) {
                        GrantStatus.GRANTED -> {
                            resetState()
                            hasShownRationaleDialog = false
                            _grantedEvents.tryEmit(Unit)
                            eventListener?.onGranted(grant, state.status)
                            onGrantedCallback?.invoke(state.status)
                            clearCallbacks(state.status)
                            currentState = FlowState.Done
                        }
                        GrantStatus.PARTIAL_GRANTED -> {
                            if (grant.requiresBackgroundUpgrade) {
                                // Landed here after an OS dialog interaction in this flow.
                                // The user must have denied the background step, so jump
                                // straight to the settings guide.
                                currentState = if (state.isFirstRequest)
                                    FlowState.HandleResult(GrantStatus.DENIED_ALWAYS, isFirstRequest = false)
                                else
                                    // Flow just started and we are currently PARTIAL.
                                    // Trigger an OS request to attempt background upgrade.
                                    FlowState.Check(isFirstRequest = false)
                            } else {
                                resetState()
                                hasShownRationaleDialog = false
                                _grantedEvents.tryEmit(Unit)
                                eventListener?.onGranted(grant, state.status)
                                onGrantedCallback?.invoke(state.status)
                                clearCallbacks(state.status)
                                currentState = FlowState.Done
                            }
                        }
                        GrantStatus.NOT_DETERMINED -> {
                            currentState = FlowState.Check(isFirstRequest = state.isFirstRequest)
                        }
                        GrantStatus.BUSY -> {
                            resetState()
                            clearCallbacks()
                            currentState = FlowState.Done
                        }
                        GrantStatus.DENIED -> {
                            if (!PlatformConfig.isRationaleSupported) {
                                // iOS has no soft-denial concept — route straight to the settings guide.
                                currentState = FlowState.HandleResult(GrantStatus.DENIED_ALWAYS, state.isFirstRequest)
                            } else if (state.isFirstRequest) {
                                if (hasShownRationaleDialog) {
                                    currentState = FlowState.HandleResult(GrantStatus.DENIED_ALWAYS, isFirstRequest = true)
                                } else {
                                    eventListener?.onDenied(grant, GrantStatus.DENIED)
                                    resetState()
                                    clearCallbacks()
                                    currentState = FlowState.Done
                                }
                            } else {
                                if (hasShownRationaleDialog) {
                                    currentState = FlowState.HandleResult(GrantStatus.DENIED_ALWAYS, isFirstRequest = false)
                                } else {
                                    hasShownRationaleDialog = true
                                    eventListener?.onRationaleShown(grant)
                                    when (ui) {
                                        is UiStrategy.StateBased -> {
                                            // Show state; flow exits — resumed by onRationaleConfirmed().
                                            updateState {
                                                it.copy(
                                                    isVisible = true,
                                                    showRationale = true,
                                                    showSettingsGuide = false,
                                                    rationaleMessage = ui.rationaleMessage,
                                                    settingsMessage = ui.settingsMessage,
                                                )
                                            }
                                            currentState = FlowState.Done
                                        }
                                        is UiStrategy.CustomUi -> {
                                            val message = ui.rationaleMessage ?: "This grant is required for this feature to work."
                                            val confirmed = suspendCancellableCoroutine { cont ->
                                                ui.onShowRationale(
                                                    message,
                                                    { if (cont.isActive) cont.resume(true) },
                                                    { if (cont.isActive) cont.resume(false) },
                                                )
                                            }
                                            if (confirmed) {
                                                val newStatus = grantManager.request(grant)
                                                _status.value = newStatus
                                                currentState = FlowState.HandleResult(newStatus, isFirstRequest = true)
                                            } else {
                                                clearCallbacks()
                                                currentState = FlowState.Done
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        GrantStatus.DENIED_ALWAYS -> {
                            when (ui) {
                                is UiStrategy.StateBased -> {
                                    // Only show the settings guide if the user has already seen the rationale
                                    // or this is not the very first request — avoids a dialog on cold-start
                                    // when the user has previously permanently denied the permission.
                                    val shouldShow = hasShownRationaleDialog || !state.isFirstRequest
                                    if (shouldShow) {
                                        eventListener?.onSettingsGuideShown(grant)
                                        updateState {
                                            it.copy(
                                                isVisible = true,
                                                showRationale = false,
                                                showSettingsGuide = true,
                                                rationaleMessage = ui.rationaleMessage,
                                                settingsMessage = ui.settingsMessage,
                                            )
                                        }
                                        // Flow exits; resumed by onSettingsConfirmed() or refreshStatus().
                                    } else {
                                        eventListener?.onDenied(grant, GrantStatus.DENIED_ALWAYS)
                                        resetState()
                                        clearCallbacks()
                                    }
                                    currentState = FlowState.Done
                                }
                                is UiStrategy.CustomUi -> {
                                    // Custom-UI always shows the settings guide so the caller's
                                    // callback has a chance to present UI regardless of history.
                                    eventListener?.onSettingsGuideShown(grant)
                                    val message = ui.settingsMessage ?: "Grant denied. Please enable it in Settings."
                                    val confirmed = suspendCancellableCoroutine { cont ->
                                        ui.onShowSettings(
                                            message,
                                            { if (cont.isActive) cont.resume(true) },
                                            { if (cont.isActive) cont.resume(false) },
                                        )
                                    }
                                    if (confirmed) {
                                        eventListener?.onSettingsOpened(grant)
                                        grantManager.openSettings()
                                    }
                                    clearCallbacks()
                                    currentState = FlowState.Done
                                }
                            }
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
     * True when at least one collector is attached to [state] — i.e. a live dialog host
     * (`GrantDialog` or a custom renderer) is observing this handler and can actually show the
     * rationale / settings-guide UI and resume a parked flow via [onRationaleConfirmed] /
     * [onSettingsConfirmed] / [onDismiss].
     *
     * Used only by [requestSuspend]'s no-host safeguard. The callback-based [request] flow
     * deliberately raises dialog state without requiring a live collector (callers may read
     * [state] however they like — the library's own tests read `state.value`), so this is NOT
     * consulted inside the state machine itself.
     *
     * Contract for suspending callers: the dialog host must be attached BEFORE the request
     * starts. `GrantDialog`'s `collectAsState` satisfies this naturally — it is in composition
     * before any button that triggers a request can be tapped.
     */
    private val hasStateHost: Boolean
        get() = _state.subscriptionCount.value > 0

    /**
     * Updates the state and persists it to savedStateDelegate.
     * This ensures state is restored after process death.
     */
    private fun updateState(block: (GrantUiState) -> GrantUiState) {
        val oldState = _state.value
        _state.update(block)
        val newState = _state.value

        // Optimization: Only save to delegate if visible state changed.
        // We only care about persisting visibility and dialog types for process death.
        if (oldState.isVisible != newState.isVisible ||
            oldState.showRationale != newState.showRationale ||
            oldState.showSettingsGuide != newState.showSettingsGuide ||
            oldState.rationaleMessage != newState.rationaleMessage ||
            oldState.settingsMessage != newState.settingsMessage
        ) {
            savedStateDelegate.saveState(KEY_IS_VISIBLE, newState.isVisible.toString())
            savedStateDelegate.saveState(KEY_SHOW_RATIONALE, newState.showRationale.toString())
            savedStateDelegate.saveState(KEY_SHOW_SETTINGS, newState.showSettingsGuide.toString())

            if (newState.rationaleMessage != null) {
                savedStateDelegate.saveState(KEY_RATIONALE_MSG, newState.rationaleMessage)
            } else {
                savedStateDelegate.clear(KEY_RATIONALE_MSG)
            }

            if (newState.settingsMessage != null) {
                savedStateDelegate.saveState(KEY_SETTINGS_MSG, newState.settingsMessage)
            } else {
                savedStateDelegate.clear(KEY_SETTINGS_MSG)
            }
        }
    }
}
