package com.yourapp.location

import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.ServiceManager

/**
 * Community-maintained helper for Location Permission + GPS service checking.
 *
 * **Usage:**
 * Copy this file to your project and customize as needed.
 *
 * **Dependencies:**
 * ```gradle
 * implementation("dev.brewkits:grant-core:1.0.0")
 * ```
 *
 * **Example:**
 * ```kotlin
 * val helper = LocationPermissionHelper(grantManager, serviceManager)
 * when (val result = helper.requestLocationWithServiceCheck()) {
 *     LocationFlowResult.Ready -> startLocationTracking()
 *     is LocationFlowResult.PermissionDenied -> showRationale()
 *     LocationFlowResult.GpsDisabled -> showEnableGpsPrompt()
 *     is LocationFlowResult.Error -> showError(result.message)
 * }
 * ```
 *
 * **See also:**
 * - Full recipe: docs/recipes/location-permission-with-gps-check.md
 * - Demo app: demo/src/commonMain/kotlin/dev/brewkits/grant/demo/LocationFlowDemoScreen.kt
 */
class LocationPermissionHelper(
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) {

    /**
     * Requests location permission and checks GPS service status.
     *
     * **Flow:**
     * 1. Request location permission
     * 2. Check if GPS is enabled
     * 3. Return combined result
     *
     * @return LocationFlowResult indicating the outcome
     */
    suspend fun requestLocationWithServiceCheck(): LocationFlowResult {
        // Step 1: Request permission
        val permissionStatus = grantManager.request(AppGrant.LOCATION)

        when (permissionStatus) {
            GrantStatus.DENIED -> {
                return LocationFlowResult.PermissionDenied(canRetry = true)
            }
            GrantStatus.DENIED_ALWAYS -> {
                return LocationFlowResult.PermissionDenied(canRetry = false)
            }
            GrantStatus.NOT_DETERMINED -> {
                return LocationFlowResult.Error("Unexpected permission state")
            }
            GrantStatus.GRANTED -> {
                // Continue to GPS check
            }
        }

        // Step 2: Check if GPS is enabled
        if (!serviceManager.isLocationEnabled()) {
            return LocationFlowResult.GpsDisabled
        }

        // Both permission AND GPS are ready!
        return LocationFlowResult.Ready
    }

    /**
     * Opens the system location settings.
     *
     * On Android, this opens the Location settings page.
     * On iOS, this opens the app's settings page (iOS doesn't have separate GPS toggle).
     */
    fun openLocationSettings() {
        serviceManager.openLocationSettings()
    }

    /**
     * Opens the app's permission settings.
     *
     * Use this when permission is denied permanently (DENIED_ALWAYS).
     */
    fun openAppSettings() {
        grantManager.openSettings()
    }

    /**
     * Checks if location permission is already granted.
     *
     * @return true if permission is granted, false otherwise
     */
    suspend fun isLocationGranted(): Boolean {
        return grantManager.checkStatus(AppGrant.LOCATION) == GrantStatus.GRANTED
    }

    /**
     * Checks if GPS service is currently enabled.
     *
     * Note: This only checks the service, not the permission.
     * Call this AFTER ensuring permission is granted.
     *
     * @return true if GPS is enabled, false otherwise
     */
    fun isGpsEnabled(): Boolean {
        return serviceManager.isLocationEnabled()
    }
}

/**
 * Result type for location permission + service check flow.
 */
sealed interface LocationFlowResult {
    /**
     * Both permission and GPS are ready.
     * You can now start location tracking.
     */
    object Ready : LocationFlowResult

    /**
     * Location permission was denied by the user.
     *
     * @param canRetry true if user can be asked again (DENIED),
     *                 false if denied permanently (DENIED_ALWAYS)
     */
    data class PermissionDenied(val canRetry: Boolean) : LocationFlowResult

    /**
     * Permission is granted, but GPS is disabled.
     * Call [LocationPermissionHelper.openLocationSettings] to guide user.
     */
    object GpsDisabled : LocationFlowResult

    /**
     * An unexpected error occurred.
     *
     * @param message Error description
     */
    data class Error(val message: String) : LocationFlowResult
}

// ============================================================================
// ADVANCED: Google Play Services Integration (Android Only)
// ============================================================================

/**
 * Advanced version with Google Play Services SettingsClient integration.
 *
 * **Additional Dependencies:**
 * ```gradle
 * implementation("dev.brewkits:grant-core:1.0.0")
 * implementation("com.google.android.gms:play-services-location:21.1.0")
 * ```
 *
 * **Features:**
 * - Shows in-app dialog to enable GPS (no manual Settings navigation)
 * - Graceful fallback if Play Services unavailable
 * - Works on Huawei/HMS devices (fallback mode)
 *
 * **Android-specific:**
 * This helper uses Play Services which is Android-only.
 * For iOS, use the basic [LocationPermissionHelper] instead.
 */
/*
import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.tasks.await

class LocationPermissionHelperWithPlayServices(
    private val context: Context,
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) {

    suspend fun requestLocationWithGpsDialog(): LocationFlowResult {
        // Step 1: Request permission first
        val permissionStatus = grantManager.request(AppGrant.LOCATION)

        if (permissionStatus != GrantStatus.GRANTED) {
            return LocationFlowResult.PermissionDenied(
                canRetry = permissionStatus != GrantStatus.DENIED_ALWAYS
            )
        }

        // Step 2: Check GPS via Play Services
        val gpsResult = checkGpsViaPlayServices()

        return when (gpsResult) {
            GpsCheckResult.Enabled -> LocationFlowResult.Ready
            GpsCheckResult.UserDeclined -> LocationFlowResult.GpsDisabled
            GpsCheckResult.PlayServicesUnavailable -> {
                // Fallback to manual check
                if (serviceManager.isLocationEnabled()) {
                    LocationFlowResult.Ready
                } else {
                    LocationFlowResult.GpsDisabled
                }
            }
        }
    }

    private suspend fun checkGpsViaPlayServices(): GpsCheckResult {
        if (!isPlayServicesAvailable()) {
            return GpsCheckResult.PlayServicesUnavailable
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L
        ).build()

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        val client = LocationServices.getSettingsClient(context)

        return try {
            client.checkLocationSettings(settingsRequest).await()
            GpsCheckResult.Enabled
        } catch (e: ApiException) {
            when (e.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    // Can show dialog to enable GPS
                    try {
                        val resolvable = e as ResolvableApiException
                        // Note: This requires Activity context
                        resolvable.startResolutionForResult(
                            context as Activity,
                            REQUEST_CHECK_SETTINGS
                        )
                        GpsCheckResult.UserDeclined // Wait for result
                    } catch (e: Exception) {
                        GpsCheckResult.PlayServicesUnavailable
                    }
                }
                else -> GpsCheckResult.PlayServicesUnavailable
            }
        }
    }

    private fun isPlayServicesAvailable(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    companion object {
        const val REQUEST_CHECK_SETTINGS = 9001
    }
}

enum class GpsCheckResult {
    Enabled,
    UserDeclined,
    PlayServicesUnavailable
}
*/
