package dev.brewkits.grant.impl

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType

/**
 * Android implementation of service checking.
 */
internal actual class PlatformServiceDelegate(
    private val context: Context
) {
    actual suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        return when (service) {
            ServiceType.LOCATION_GPS -> checkLocationService()
            ServiceType.BLUETOOTH -> checkBluetoothService()
            ServiceType.WIFI -> checkWifiService()
            ServiceType.NFC -> checkNfcService()
            ServiceType.CAMERA_HARDWARE -> checkCameraHardware()
        }
    }

    actual suspend fun openServiceSettings(service: ServiceType): Boolean {
        return try {
            val intent = when (service) {
                ServiceType.LOCATION_GPS -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                ServiceType.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                ServiceType.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
                ServiceType.NFC -> Intent(Settings.ACTION_NFC_SETTINGS)
                ServiceType.CAMERA_HARDWARE -> Intent(Settings.ACTION_SETTINGS)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkLocationService(): ServiceStatus {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return ServiceStatus.NOT_AVAILABLE

            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            when {
                isGpsEnabled || isNetworkEnabled -> ServiceStatus.ENABLED
                else -> ServiceStatus.DISABLED
            }
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }

    private fun checkBluetoothService(): ServiceStatus {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: return ServiceStatus.NOT_AVAILABLE

            if (bluetoothAdapter.isEnabled) {
                ServiceStatus.ENABLED
            } else {
                ServiceStatus.DISABLED
            }
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }

    private fun checkWifiService(): ServiceStatus {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return ServiceStatus.NOT_AVAILABLE

            @Suppress("DEPRECATION")
            if (wifiManager.isWifiEnabled) {
                ServiceStatus.ENABLED
            } else {
                ServiceStatus.DISABLED
            }
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }

    private fun checkNfcService(): ServiceStatus {
        return try {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                ?: return ServiceStatus.NOT_AVAILABLE

            if (nfcAdapter.isEnabled) {
                ServiceStatus.ENABLED
            } else {
                ServiceStatus.DISABLED
            }
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }

    private fun checkCameraHardware(): ServiceStatus {
        return try {
            val hasCamera = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
            } else {
                context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA)
            }

            if (hasCamera) {
                ServiceStatus.ENABLED
            } else {
                ServiceStatus.NOT_AVAILABLE
            }
        } catch (e: Exception) {
            ServiceStatus.UNKNOWN
        }
    }
}
