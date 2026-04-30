package dev.brewkits.grant

/**
 * A utility that combines OS permission checks and hardware service monitoring
 * into a single unified API.
 *
 * In production mobile development, a permission being [GrantStatus.GRANTED]
 * is often insufficient to use a feature. For example, Location features require
 * BOTH the permission and the system GPS toggle to be enabled.
 *
 * [GrantAndServiceChecker] is considered the **Best Practice** for verifying
 * full feature readiness.
 *
 * ### Usage Example
 * ```kotlin
 * val checker = GrantAndServiceChecker(grantManager, serviceManager)
 *
 * suspend fun startLocationFeature() {
 *     when (val status = checker.checkLocationReady()) {
 *         LocationReadyStatus.Ready -> sensor.start()
 *         LocationReadyStatus.ServiceDisabled -> ui.showEnableGpsPrompt()
 *         LocationReadyStatus.GrantDenied -> grantHandler.request { ... }
 *         LocationReadyStatus.BothRequired -> ui.showTotalFailure()
 *     }
 * }
 * ```
 */
class GrantAndServiceChecker(
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) {
    /**
     * Checks if location features are fully ready (Permission + GPS hardware).
     *
     * @param grant The specific location permission to check (defaults to [AppGrant.LOCATION]).
     * @return A [LocationReadyStatus] representing the aggregate state.
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
     * Checks if Bluetooth features are fully ready (Permission + Radio hardware).
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
     * Performs a generic readiness check for any permission/service combination.
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
     * A simplified convenience API to check if a feature is fully ready.
     *
     * Automatically maps common permissions to their corresponding hardware services:
     * - [AppGrant.LOCATION] -> [ServiceType.LOCATION_GPS]
     * - [AppGrant.BLUETOOTH] -> [ServiceType.BLUETOOTH]
     */
    suspend fun isReady(grant: AppGrant): Boolean {
        val hasPermission = grantManager.checkStatus(grant) == GrantStatus.GRANTED
        val serviceType = when (grant) {
            AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS -> ServiceType.LOCATION_GPS
            AppGrant.BLUETOOTH -> ServiceType.BLUETOOTH
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
 * Aggregate status for Location feature readiness.
 */
sealed class LocationReadyStatus {
    /** Feature is fully functional. */
    object Ready : LocationReadyStatus()

    /** Permission is missing; hardware status is secondary. */
    data class GrantDenied(val grantStatus: GrantStatus) : LocationReadyStatus()

    /** Permission is OK, but GPS hardware is disabled. */
    object ServiceDisabled : LocationReadyStatus()

    /** Both the OS permission and the hardware service are disabled. */
    data class BothRequired(val grantStatus: GrantStatus) : LocationReadyStatus()

    /** Status could not be determined. */
    object Unknown : LocationReadyStatus()
}

/**
 * Aggregate status for Bluetooth feature readiness.
 */
sealed class BluetoothReadyStatus {
    object Ready : BluetoothReadyStatus()
    data class GrantDenied(val grantStatus: GrantStatus) : BluetoothReadyStatus()
    object ServiceDisabled : BluetoothReadyStatus()
    data class BothRequired(val grantStatus: GrantStatus) : BluetoothReadyStatus()
    object Unknown : BluetoothReadyStatus()
}

/**
 * A generic data structure representing the aggregate state of a permission and service.
 */
data class ReadyStatus(
    val grantStatus: GrantStatus,
    val serviceStatus: ServiceStatus,
    val isReady: Boolean
) {
    /**
     * A human-readable diagnostic message.
     */
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
