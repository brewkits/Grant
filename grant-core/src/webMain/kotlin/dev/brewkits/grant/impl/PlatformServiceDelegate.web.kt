package dev.brewkits.grant.impl

import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.js.navigatorJs
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.await

private const val TAG = "WebServiceDelegate"

/**
 * Only [ServiceType.CAMERA_HARDWARE] has a real, standard browser signal:
 * `MediaDevices.enumerateDevices()` lists device *presence* (kind == "videoinput") without
 * requiring a permission grant first. Every other [ServiceType] — Bluetooth adapter state,
 * Wi-Fi radio state, NFC, GPS-as-distinct-from-permission, health data — has no standard,
 * cross-browser way to read "is the hardware/radio itself on", as opposed to "has the page
 * been granted the corresponding permission". Reporting [ServiceStatus.ENABLED] for those
 * would be a guess dressed up as a fact, so they report [ServiceStatus.UNKNOWN] instead —
 * [dev.brewkits.grant.impl.MyServiceManager] already treats that as its safe fallback for
 * every platform, so this is consistent with the existing contract, not a special case.
 */
internal actual class PlatformServiceDelegate {

    actual suspend fun checkServiceStatus(service: ServiceType): ServiceStatus = when (service) {
        ServiceType.CAMERA_HARDWARE -> checkCameraHardware()
        else -> {
            GrantLogger.d(TAG, "ServiceType.$service has no standard browser signal; reporting UNKNOWN.")
            ServiceStatus.UNKNOWN
        }
    }

    actual suspend fun openServiceSettings(service: ServiceType): Boolean {
        GrantLogger.w(TAG, "openServiceSettings() has no browser equivalent for $service.")
        return false
    }

    private suspend fun checkCameraHardware(): ServiceStatus = try {
        val devices = navigatorJs.mediaDevices.enumerateDevices().await()
        val hasCamera = devices.toArray().any { it.kind == "videoinput" }
        if (hasCamera) ServiceStatus.ENABLED else ServiceStatus.NOT_AVAILABLE
    } catch (e: Throwable) {
        GrantLogger.d(TAG, "enumerateDevices() failed: ${e.message}")
        ServiceStatus.UNKNOWN
    }
}
