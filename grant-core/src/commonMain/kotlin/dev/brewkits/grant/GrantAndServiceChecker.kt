package dev.brewkits.grant

/**
 * Combined checker for grants and services.
 *
 * **Why is this useful?**
 * - Grant GRANTED doesn't mean feature works
 * - Need to check both grant AND service
 * - Provides clear status for UI
 *
 * **Example**:
 * ```kotlin
 * val checker = GrantAndServiceChecker(grantManager, serviceManager)
 *
 * when (checker.checkLocationReady()) {
 *     LocationReadyStatus.Ready -> startTracking()
 *     LocationReadyStatus.GrantDenied -> showGrantDialog()
 *     LocationReadyStatus.ServiceDisabled -> showEnableGPSDialog()
 *     LocationReadyStatus.Both -> showBothRequiredDialog()
 * }
 * ```
 */
class GrantAndServiceChecker(
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) {
    /**
     * Check if location is ready to use (both grant and GPS enabled).
     * @param grant The specific location grant to check (defaults to LOCATION)
     */
    suspend fun checkLocationReady(grant: AppGrant = AppGrant.LOCATION): LocationReadyStatus {
        val grantStatus = grantManager.checkStatus(grant)
        val serviceStatus = serviceManager.checkServiceStatus(ServiceType.LOCATION_GPS)

        return when {
            grantStatus == GrantStatus.GRANTED && serviceStatus == ServiceStatus.ENABLED ->
                LocationReadyStatus.Ready

            grantStatus != GrantStatus.GRANTED && serviceStatus != ServiceStatus.ENABLED ->
                LocationReadyStatus.BothRequired(grantStatus)

            grantStatus != GrantStatus.GRANTED ->
                LocationReadyStatus.GrantDenied(grantStatus)

            serviceStatus != ServiceStatus.ENABLED ->
                LocationReadyStatus.ServiceDisabled

            else -> LocationReadyStatus.Unknown
        }
    }

    /**
     * Check if Bluetooth is ready to use.
     */
    suspend fun checkBluetoothReady(): BluetoothReadyStatus {
        val grantStatus = grantManager.checkStatus(AppGrant.BLUETOOTH)
        val serviceStatus = serviceManager.checkServiceStatus(ServiceType.BLUETOOTH)

        return when {
            grantStatus == GrantStatus.GRANTED && serviceStatus == ServiceStatus.ENABLED ->
                BluetoothReadyStatus.Ready

            grantStatus != GrantStatus.GRANTED && serviceStatus != ServiceStatus.ENABLED ->
                BluetoothReadyStatus.BothRequired(grantStatus)

            grantStatus != GrantStatus.GRANTED ->
                BluetoothReadyStatus.GrantDenied(grantStatus)

            serviceStatus != ServiceStatus.ENABLED ->
                BluetoothReadyStatus.ServiceDisabled

            else -> BluetoothReadyStatus.Unknown
        }
    }

    /**
     * Generic check for any grant + service combination.
     */
    suspend fun checkReady(
        grant: AppGrant,
        serviceType: ServiceType
    ): ReadyStatus {
        val grantStatus = grantManager.checkStatus(grant)
        val serviceStatus = serviceManager.checkServiceStatus(serviceType)

        return ReadyStatus(
            grantStatus = grantStatus,
            serviceStatus = serviceStatus,
            isReady = grantStatus == GrantStatus.GRANTED && serviceStatus == ServiceStatus.ENABLED
        )
    }

    /**
     * Convenience API to quickly check if a feature mapped to an AppGrant is fully ready 
     * (permission granted AND hardware enabled if applicable).
     * 
     * Mappings:
     * - LOCATION -> LOCATION_GPS
     * - BLUETOOTH -> BLUETOOTH
     * - Default -> Only checks permission status
     */
    suspend fun isReady(grant: AppGrant): Boolean {
        val hasPermission = grantManager.checkStatus(grant) == GrantStatus.GRANTED
        val serviceType = when (grant) {
            AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS -> ServiceType.LOCATION_GPS
            AppGrant.BLUETOOTH -> ServiceType.BLUETOOTH
            // Add other mappings if they correspond to specific hardware services
            else -> null
        }
        
        return if (serviceType != null) {
            hasPermission && serviceManager.checkServiceStatus(serviceType) == ServiceStatus.ENABLED
        } else {
            hasPermission
        }
    }
}

/**
 * Location readiness status.
 */
sealed class LocationReadyStatus {
    /** Location is ready to use */
    object Ready : LocationReadyStatus()

    /** Grant denied, service doesn't matter */
    data class GrantDenied(val grantStatus: GrantStatus) : LocationReadyStatus()

    /** Grant OK, but GPS disabled */
    object ServiceDisabled : LocationReadyStatus()

    /** Both grant denied AND GPS disabled */
    data class BothRequired(val grantStatus: GrantStatus) : LocationReadyStatus()

    /** Unable to determine */
    object Unknown : LocationReadyStatus()
}

/**
 * Bluetooth readiness status.
 */
sealed class BluetoothReadyStatus {
    object Ready : BluetoothReadyStatus()
    data class GrantDenied(val grantStatus: GrantStatus) : BluetoothReadyStatus()
    object ServiceDisabled : BluetoothReadyStatus()
    data class BothRequired(val grantStatus: GrantStatus) : BluetoothReadyStatus()
    object Unknown : BluetoothReadyStatus()
}

/**
 * Generic readiness status.
 */
data class ReadyStatus(
    val grantStatus: GrantStatus,
    val serviceStatus: ServiceStatus,
    val isReady: Boolean
) {
    val message: String
        get() = when {
            isReady -> "Ready to use"
            grantStatus != GrantStatus.GRANTED && serviceStatus != ServiceStatus.ENABLED ->
                "Grant and service both required"
            grantStatus != GrantStatus.GRANTED ->
                "Grant required: $grantStatus"
            serviceStatus != ServiceStatus.ENABLED ->
                "Service required: $serviceStatus"
            else -> "Unknown status"
        }
}
