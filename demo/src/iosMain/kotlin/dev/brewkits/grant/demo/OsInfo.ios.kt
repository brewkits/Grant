package dev.brewkits.grant.demo

import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

actual object OsInfo {
    actual val platform: String = "iOS"

    actual val osVersion: String
        get() {
            val version = UIDevice.currentDevice.systemVersion
            return "iOS $version"
        }

    actual val apiLevel: String
        get() {
            val version = UIDevice.currentDevice.systemVersion
            return "iOS $version"
        }

    actual fun galleryBehaviorNote(): String {
        val major = iosMajorVersion()
        return "iOS $major: PHPhotoLibrary — LIMITED → PARTIAL_GRANTED"
    }

    actual fun notificationBehaviorNote(): String {
        val major = iosMajorVersion()
        return "iOS $major: UNUserNotificationCenter.requestAuthorization"
    }

    actual fun motionBehaviorNote(): String {
        val major = iosMajorVersion()
        return "iOS $major: CMMotionActivityManager — Dummy Query pattern"
    }

    private fun iosMajorVersion(): Int {
        val versionString = UIDevice.currentDevice.systemVersion
        return versionString.split(".").firstOrNull()?.toIntOrNull() ?: 0
    }
}
