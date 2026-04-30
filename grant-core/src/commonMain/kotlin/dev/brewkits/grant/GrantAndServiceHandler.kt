package dev.brewkits.grant

import kotlinx.coroutines.CoroutineScope
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
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(GrantAndServiceUiState())

    /**
     * Observable state for driving the UI.
     */
    val state: StateFlow<GrantAndServiceUiState> = _state.asStateFlow()

    @kotlin.concurrent.Volatile
    private var onReadyCallback: (() -> Unit)? = null

    // Stored so onRationaleConfirmed() can pass them to processFlow/handlePermissionStatus
    @kotlin.concurrent.Volatile private var pendingRationale: String? = null
    @kotlin.concurrent.Volatile private var pendingPermSettings: String? = null
    @kotlin.concurrent.Volatile private var pendingSvcSettings: String? = null

    private val requestMutex = Mutex()

    init {
        refreshStatus()
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
        if (!requestMutex.tryLock()) return
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
                _state.update { it.copy(isVisible = false, showRationale = false) }
                val status = grantManager.request(grant)
                if (status == GrantStatus.GRANTED || status == GrantStatus.PARTIAL_GRANTED) {
                    checkServiceAndFinish(pendingSvcSettings)
                } else {
                    handlePermissionStatus(status, settings = pendingPermSettings)
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
    ) {
        // 1. Check Permission
        val permStatus = grantManager.checkStatus(grant)
        if (permStatus == GrantStatus.NOT_DETERMINED) {
            val result = grantManager.request(grant)
            if (result == GrantStatus.GRANTED || result == GrantStatus.PARTIAL_GRANTED) {
                checkServiceAndFinish(svcSettings)
            } else {
                // First system request denied — don't show rationale yet (matches GrantHandler UX).
                resetState()
                onReadyCallback = null
            }
            return
        }

        if (permStatus != GrantStatus.GRANTED && permStatus != GrantStatus.PARTIAL_GRANTED) {
            handlePermissionStatus(permStatus, rationale, permSettings)
            return
        }

        // 2. Check Service
        checkServiceAndFinish(svcSettings)
    }

    private suspend fun checkServiceAndFinish(svcSettings: String?) {
        val svcStatus = serviceManager.checkServiceStatus(serviceType)
        if (svcStatus != ServiceStatus.ENABLED) {
            _state.update {
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
                _state.update {
                    it.copy(
                        isVisible = true,
                        showRationale = true,
                        rationaleMessage = rationale
                    )
                }
            }
            GrantStatus.DENIED_ALWAYS -> {
                _state.update {
                    it.copy(
                        isVisible = true,
                        showPermissionSettings = true,
                        permissionSettingsMessage = settings
                    )
                }
            }
            else -> {}
        }
    }

    private suspend fun checkInternal(): Boolean {
        val permOk = grantManager.checkStatus(grant).let { 
            it == GrantStatus.GRANTED || it == GrantStatus.PARTIAL_GRANTED 
        }
        val svcOk = serviceManager.checkServiceStatus(serviceType) == ServiceStatus.ENABLED
        return permOk && svcOk
    }

    private fun resetState() {
        _state.update {
            it.copy(
                isVisible = false,
                showRationale = false,
                showPermissionSettings = false,
                showServiceSettings = false
            )
        }
    }
}
