package dev.brewkits.grant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * UI State for the unified Grant and Service flow.
 */
data class GrantAndServiceUiState(
    /** Whether any dialog should be visible. */
    val isVisible: Boolean = false,
    
    /** Whether to show a permission rationale dialog. */
    val showRationale: Boolean = false,
    
    /** Whether to show a settings guide for a permanently denied permission. */
    val showPermissionSettings: Boolean = false,
    
    /** Whether to show a guide to enable a hardware service (e.g., GPS). */
    val showServiceSettings: Boolean = false,
    
    /** Custom message for permission rationale. */
    val rationaleMessage: String? = null,
    
    /** Custom message for permission settings guide. */
    val permissionSettingsMessage: String? = null,
    
    /** Custom message for hardware service enablement guide. */
    val serviceSettingsMessage: String? = null,
    
    /** The overall readiness status. */
    val isReady: Boolean = false
)

/**
 * A unified handler that orchestrates both OS permission requests and hardware 
 * service enablement (e.g., GPS, Bluetooth).
 * 
 * In production apps, features like Location often require two steps:
 * 1. Requesting the OS permission.
 * 2. Ensuring the GPS hardware is actually turned ON.
 * 
 * [GrantAndServiceHandler] automates this entire sequence into a single 
 * observable flow.
 * 
 * ### Behavior Flow
 * 1. Check Permission → If [GrantStatus.GRANTED], move to step 2.
 * 2. Check Service → If [ServiceStatus.ENABLED], execute `onReady`.
 * 3. Otherwise, show appropriate dialogs for each missing piece.
 * 
 * ### Usage Example
 * ```kotlin
 * val locationHandler = GrantAndServiceHandler(
 *     grantManager = grantManager,
 *     serviceManager = serviceManager,
 *     grant = AppGrant.LOCATION,
 *     serviceType = ServiceType.LOCATION_GPS,
 *     scope = viewModelScope
 * )
 * 
 * fun startFeature() {
 *     locationHandler.request(
 *         rationaleMessage = "Location is needed to track your run.",
 *         serviceSettingsMessage = "Please turn on GPS to start tracking."
 *     ) {
 *         // Both Permission and GPS are now active!
 *         sensor.start()
 *     }
 * }
 * ```
 */
class GrantAndServiceHandler(
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager,
    private val grant: GrantPermission,
    private val serviceType: ServiceType,
    private val scope: CoroutineScope,
    private val savedStateDelegate: SavedStateDelegate = NoOpSavedStateDelegate()
) {
    private companion object {
        const val KEY_IS_VISIBLE      = "gsh_is_visible"
        const val KEY_SHOW_RATIONALE  = "gsh_show_rationale"
        const val KEY_SHOW_PERM_SET   = "gsh_show_perm_settings"
        const val KEY_SHOW_SVC_SET    = "gsh_show_svc_settings"
        const val KEY_RATIONALE_MSG   = "gsh_rationale_msg"
        const val KEY_PERM_SET_MSG    = "gsh_perm_settings_msg"
        const val KEY_SVC_SET_MSG     = "gsh_svc_settings_msg"
        
        const val TAG = "GrantAndServiceHandler"
    }

    private val _state = MutableStateFlow(GrantAndServiceUiState())

    /**
     * Observable state for driving the UI.
     */
    val state: StateFlow<GrantAndServiceUiState> = _state.asStateFlow()

    @kotlin.concurrent.Volatile
    private var onReadyCallback: (() -> Unit)? = null

    @kotlin.concurrent.Volatile private var pendingRationale: String? = null
    @kotlin.concurrent.Volatile private var pendingPermSettings: String? = null
    @kotlin.concurrent.Volatile private var pendingSvcSettings: String? = null

    private val requestMutex = Mutex()

    init {
        // Restore state from delegate (process death)
        val wasVisible      = savedStateDelegate.restoreState(KEY_IS_VISIBLE)?.toBoolean() ?: false
        val wasRationale    = savedStateDelegate.restoreState(KEY_SHOW_RATIONALE)?.toBoolean() ?: false
        val wasPermSettings = savedStateDelegate.restoreState(KEY_SHOW_PERM_SET)?.toBoolean() ?: false
        val wasSvcSettings  = savedStateDelegate.restoreState(KEY_SHOW_SVC_SET)?.toBoolean() ?: false
        
        pendingRationale    = savedStateDelegate.restoreState(KEY_RATIONALE_MSG)
        pendingPermSettings = savedStateDelegate.restoreState(KEY_PERM_SET_MSG)
        pendingSvcSettings  = savedStateDelegate.restoreState(KEY_SVC_SET_MSG)
        
        if (wasVisible) {
            _state.update {
                it.copy(
                    isVisible = true,
                    showRationale = wasRationale,
                    showPermissionSettings = wasPermSettings,
                    showServiceSettings = wasSvcSettings,
                    rationaleMessage = pendingRationale,
                    permissionSettingsMessage = pendingPermSettings,
                    serviceSettingsMessage = pendingSvcSettings
                )
            }
        }

        refreshStatus()
    }

    private fun updateState(block: (GrantAndServiceUiState) -> GrantAndServiceUiState) {
        val oldState = _state.value
        val newState = block(oldState)
        _state.value = newState
        
        if (oldState.isVisible != newState.isVisible ||
            oldState.showRationale != newState.showRationale ||
            oldState.showPermissionSettings != newState.showPermissionSettings ||
            oldState.showServiceSettings != newState.showServiceSettings
        ) {
            savedStateDelegate.saveState(KEY_IS_VISIBLE, newState.isVisible.toString())
            savedStateDelegate.saveState(KEY_SHOW_RATIONALE, newState.showRationale.toString())
            savedStateDelegate.saveState(KEY_SHOW_PERM_SET, newState.showPermissionSettings.toString())
            savedStateDelegate.saveState(KEY_SHOW_SVC_SET, newState.showServiceSettings.toString())
            
            if (pendingRationale != null) savedStateDelegate.saveState(KEY_RATIONALE_MSG, pendingRationale!!)
            else savedStateDelegate.clear(KEY_RATIONALE_MSG)
            
            if (pendingPermSettings != null) savedStateDelegate.saveState(KEY_PERM_SET_MSG, pendingPermSettings!!)
            else savedStateDelegate.clear(KEY_PERM_SET_MSG)
            
            if (pendingSvcSettings != null) savedStateDelegate.saveState(KEY_SVC_SET_MSG, pendingSvcSettings!!)
            else savedStateDelegate.clear(KEY_SVC_SET_MSG)
        }
    }

    /**
     * Re-checks both permission and hardware service status.
     * If both are ready and there is a pending onReadyCallback, it will be executed.
     */
    fun refreshStatus() {
        scope.launch {
            val isReady = checkInternal()
            _state.update { it.copy(isReady = isReady) }
            if (isReady) {
                val callback = onReadyCallback
                if (callback != null) {
                    resetState()
                    onReadyCallback = null
                    callback.invoke()
                }
            }
        }
    }

    /**
     * Initiates the unified request flow.
     * 
     * @param rationaleMessage Text for the permission rationale dialog.
     * @param permissionSettingsMessage Text for the permission settings guide.
     * @param serviceSettingsMessage Text for the hardware service settings guide.
     * @param onReady Callback executed ONLY when both permission and service are ready.
     */
    fun request(
        rationaleMessage: String? = null,
        permissionSettingsMessage: String? = null,
        serviceSettingsMessage: String? = null,
        onReady: () -> Unit
    ) {
        if (!requestMutex.tryLock()) {
            dev.brewkits.grant.utils.GrantLogger.d(TAG, "Request for ${grant.identifier} is BUSY.")
            return
        }
        this.onReadyCallback = onReady
        pendingRationale = rationaleMessage
        pendingPermSettings = permissionSettingsMessage
        pendingSvcSettings = serviceSettingsMessage

        val job = scope.launch {
            try {
                processFlow(rationaleMessage, permissionSettingsMessage, serviceSettingsMessage)
            } finally {
                requestMutex.unlock()
            }
        }

        if (!job.isActive) {
            requestMutex.unlock()
        }
    }

    /**
     * Confirms the rationale and proceeds to request the OS permission.
     */
    fun onRationaleConfirmed() {
        if (!requestMutex.tryLock()) return
        val job = scope.launch {
            try {
                updateState { it.copy(isVisible = false, showRationale = false) }
                val status = grantManager.request(grant)
                if (status == GrantStatus.GRANTED || status == GrantStatus.PARTIAL_GRANTED) {
                    checkServiceAndFinish(pendingSvcSettings)
                } else {
                    handlePermissionStatus(status, rationale = pendingRationale, settings = pendingPermSettings)
                }
            } finally {
                requestMutex.unlock()
            }
        }

        if (!job.isActive) {
            requestMutex.unlock()
        }
    }

    /**
     * Confirms and opens system settings for the permission.
     */
    fun onPermissionSettingsConfirmed() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            onReadyCallback = null
            pendingRationale = null
            pendingPermSettings = null
            pendingSvcSettings = null
            grantManager.openSettings()
        } finally {
            requestMutex.unlock()
        }
    }

    /**
     * Confirms and opens system settings for the hardware service.
     */
    fun onServiceSettingsConfirmed() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            onReadyCallback = null
            pendingRationale = null
            pendingPermSettings = null
            pendingSvcSettings = null
            scope.launch {
                serviceManager.openServiceSettings(serviceType)
            }
        } finally {
            requestMutex.unlock()
        }
    }

    /**
     * Dismisses the current dialog and cancels the flow.
     */
    fun onDismiss() {
        if (!requestMutex.tryLock()) return
        try {
            resetState()
            onReadyCallback = null
            pendingRationale = null
            pendingPermSettings = null
            pendingSvcSettings = null
        } finally {
            requestMutex.unlock()
        }
    }

    private suspend fun processFlow(
        rationale: String? = null,
        permSettings: String? = null,
        svcSettings: String? = null
    ) = coroutineScope {
        val permStatusDef = async { grantManager.checkStatus(grant) }
        val svcStatusDef = async { serviceManager.checkServiceStatus(serviceType) }

        val permStatus = permStatusDef.await()
        val svcStatus = svcStatusDef.await()

        if (permStatus == GrantStatus.NOT_DETERMINED || (permStatus == GrantStatus.PARTIAL_GRANTED && grant.requiresBackgroundUpgrade)) {
            val result = grantManager.request(grant)
            if (isSufficient(result)) {
                checkServiceAndFinishWithStatus(svcStatus, svcSettings)
            } else {
                if (result == GrantStatus.PARTIAL_GRANTED) {
                    // User denied the background step — route straight to settings.
                    handlePermissionStatus(GrantStatus.DENIED_ALWAYS, rationale, permSettings)
                } else if (result == GrantStatus.DENIED) {
                    // Show rationale immediately after first denial, consistent with GrantHandler UX.
                    handlePermissionStatus(GrantStatus.DENIED, rationale, permSettings)
                } else {
                    resetState()
                    onReadyCallback = null
                }
            }
            return@coroutineScope
        }

        if (!isSufficient(permStatus)) {
            handlePermissionStatus(permStatus, rationale, permSettings)
            return@coroutineScope
        }

        // 2. Permission is OK, check Service
        checkServiceAndFinishWithStatus(svcStatus, svcSettings)
    }

    private suspend fun checkServiceAndFinish(svcSettings: String?) {
        val svcStatus = serviceManager.checkServiceStatus(serviceType)
        checkServiceAndFinishWithStatus(svcStatus, svcSettings)
    }

    private fun checkServiceAndFinishWithStatus(svcStatus: ServiceStatus, svcSettings: String?) {
        if (svcStatus != ServiceStatus.ENABLED) {
            updateState {
                it.copy(
                    isVisible = true,
                    showServiceSettings = true,
                    serviceSettingsMessage = svcSettings
                )
            }
            return
        }
        resetState()
        _state.update { it.copy(isReady = true) }
        val callback = onReadyCallback
        onReadyCallback = null
        callback?.invoke()
    }

    private fun handlePermissionStatus(
        status: GrantStatus,
        rationale: String? = null,
        settings: String? = null
    ) {
        when (status) {
            GrantStatus.NOT_DETERMINED, GrantStatus.DENIED -> {
                updateState {
                    it.copy(
                        isVisible = true,
                        showRationale = true,
                        rationaleMessage = rationale,
                        permissionSettingsMessage = settings,
                        serviceSettingsMessage = pendingSvcSettings
                    )
                }
            }
            GrantStatus.DENIED_ALWAYS, GrantStatus.PARTIAL_GRANTED -> {
                updateState {
                    it.copy(
                        isVisible = true,
                        showPermissionSettings = true,
                        permissionSettingsMessage = settings,
                        rationaleMessage = rationale,
                        serviceSettingsMessage = pendingSvcSettings
                    )
                }
            }
            else -> {}
        }
    }

    private suspend fun checkInternal(): Boolean = coroutineScope {
        val permStatusDef = async { grantManager.checkStatus(grant) }
        val svcStatusDef = async { serviceManager.checkServiceStatus(serviceType) }
        
        val permStatus = permStatusDef.await()
        val permOk = isSufficient(permStatus)
        
        val svcOk = svcStatusDef.await() == ServiceStatus.ENABLED
        return@coroutineScope permOk && svcOk
    }

    private fun isSufficient(status: GrantStatus): Boolean {
        return when (status) {
            GrantStatus.GRANTED -> true
            GrantStatus.PARTIAL_GRANTED -> !grant.requiresBackgroundUpgrade
            else -> false
        }
    }

    private fun resetState() {
        updateState {
            it.copy(
                isVisible = false,
                showRationale = false,
                showPermissionSettings = false,
                showServiceSettings = false
            )
        }
        pendingRationale = null
        pendingPermSettings = null
        pendingSvcSettings = null
    }
}
