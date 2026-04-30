package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.utils.GrantLogger
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSClassFromString
import platform.Foundation.valueForKey
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val TAG = "iOSServiceDelegate"

/**
 * iOS implementation of service checking.
 */
internal actual class PlatformServiceDelegate {
    actual suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        return when (service) {
            ServiceType.LOCATION_GPS    -> checkLocationService()
            ServiceType.BLUETOOTH      -> checkBluetoothService()
            ServiceType.WIFI           -> ServiceStatus.ENABLED  // iOS doesn't expose WiFi on/off
            ServiceType.NFC            -> ServiceStatus.NOT_AVAILABLE // iOS NFC is automatic
            ServiceType.CAMERA_HARDWARE -> ServiceStatus.ENABLED  // All modern iOS devices have cameras
            ServiceType.HEALTH         -> checkHealthService()
        }
    }

    /**
     * Opens the app's system settings page.
     *
     * Uses the KVC-safe UIApplication accessor pattern identical to
     * [PlatformGrantDelegate.openSettings] to avoid crashes in App Extension
     * contexts (e.g. Notification Service Extension, Share Extension) where
     * accessing `UIApplication.sharedApplication` is forbidden.
     */
    actual suspend fun openServiceSettings(service: ServiceType): Boolean {
        return try {
            val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return false

            val isExtension = NSBundle.mainBundle.bundlePath.endsWith(".appex")
            if (isExtension) {
                GrantLogger.w(TAG, "Cannot open Settings from an App Extension context.")
                return false
            }

            val sharedApp = (NSClassFromString("UIApplication") as? platform.darwin.NSObject)
                ?.valueForKey("sharedApplication") as? UIApplication
            if (sharedApp == null) {
                GrantLogger.w(TAG, "UIApplication.sharedApplication is not accessible in this context.")
                return false
            }

            dispatch_async(dispatch_get_main_queue()) {
                sharedApp.openURL(
                    settingsUrl,
                    options = emptyMap<Any?, Any?>(),
                    completionHandler = null
                )
            }
            true
        } catch (e: Exception) {
            GrantLogger.e(TAG, "Failed to open service settings", e)
            false
        }
    }

    private fun checkLocationService(): ServiceStatus {
        return try {
            if (CLLocationManager.locationServicesEnabled()) ServiceStatus.ENABLED else ServiceStatus.DISABLED
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }

    /**
     * Checking Bluetooth power state on iOS requires creating a [CBCentralManager]
     * and registering a delegate — a side-effecting operation that also triggers
     * the Bluetooth permission prompt. To avoid that and avoid static-linking
     * CoreBluetooth into PlatformServiceDelegate, we return [ServiceStatus.UNKNOWN].
     *
     * Use [GrantManager.checkStatus] with [AppGrant.BLUETOOTH] to check authorization
     * state instead.
     */
    private fun checkBluetoothService(): ServiceStatus = ServiceStatus.UNKNOWN

    /**
     * Checking HealthKit availability requires statically linking platform.HealthKit,
     * which causes Apple to require NSHealthShareUsageDescription in Info.plist for
     * all apps using ServiceManager — even those that don't use Health features.
     *
     * Apps that need Health integration should check availability directly via
     * HKHealthStore.isHealthDataAvailable() in their own code.
     */
    private fun checkHealthService(): ServiceStatus {
        GrantLogger.i(TAG, "HealthKit service check is not available in grant-core to avoid forced framework linking. Check HKHealthStore.isHealthDataAvailable() directly in your app.")
        return ServiceStatus.NOT_AVAILABLE
    }
}
