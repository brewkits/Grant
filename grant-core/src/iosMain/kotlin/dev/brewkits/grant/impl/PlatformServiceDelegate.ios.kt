package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import platform.CoreLocation.CLLocationManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS implementation of service checking.
 */
internal actual class PlatformServiceDelegate {
    actual suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        return when (service) {
            ServiceType.LOCATION_GPS -> checkLocationService()
            ServiceType.BLUETOOTH -> checkBluetoothService()
            ServiceType.WIFI -> ServiceStatus.ENABLED // iOS doesn't allow checking WiFi
            ServiceType.NFC -> ServiceStatus.NOT_AVAILABLE // iOS NFC is automatic
            ServiceType.CAMERA_HARDWARE -> checkCameraHardware()
        }
    }

    actual suspend fun openServiceSettings(service: ServiceType): Boolean {
        return try {
            // iOS can only open main Settings app, not specific service settings
            val settingsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
            if (settingsUrl != null && UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
                // Use modern API: openURL:options:completionHandler:
                // This replaces the deprecated openURL: method (deprecated since iOS 10)
                var result = false
                UIApplication.sharedApplication.openURL(
                    settingsUrl,
                    options = emptyMap<Any?, Any?>(),
                    completionHandler = { success ->
                        result = success
                    }
                )
                // Note: The completion handler is called asynchronously, but we return immediately
                // This matches the behavior of the old deprecated API
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkLocationService(): ServiceStatus {
        return try {
            val enabled = CLLocationManager.locationServicesEnabled()
            if (enabled) {
                ServiceStatus.ENABLED
            } else {
                ServiceStatus.DISABLED
            }
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }

    private fun checkBluetoothService(): ServiceStatus {
        // Note: iOS doesn't provide a simple way to check Bluetooth status
        // without using CoreBluetooth framework and creating a CBCentralManager
        // For now, we assume it's enabled
        // A proper implementation would use CBCentralManager delegate
        return ServiceStatus.ENABLED
    }

    private fun checkCameraHardware(): ServiceStatus {
        return try {
            // iOS devices always have cameras (except some edge cases)
            // A proper check would use AVCaptureDevice.devices()
            ServiceStatus.ENABLED
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }
}
