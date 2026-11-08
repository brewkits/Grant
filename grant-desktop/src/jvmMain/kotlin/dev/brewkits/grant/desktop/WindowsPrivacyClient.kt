package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant

internal class WindowsPrivacyClient(private val bridge: WindowsBridgeLibrary) {
    fun checkStatus(grant: AppGrant): WindowsPrivacyStatus {
        val permission = permissionId(grant) ?: return WindowsPrivacyStatus.UNAVAILABLE
        return when (bridge.grant_windows_access_status(permission)) {
            0 -> WindowsPrivacyStatus.UNSPECIFIED
            1 -> WindowsPrivacyStatus.ALLOWED
            2 -> WindowsPrivacyStatus.DENIED_BY_USER
            3 -> WindowsPrivacyStatus.DENIED_BY_SYSTEM
            else -> WindowsPrivacyStatus.UNAVAILABLE
        }
    }

    fun openSettings(grant: AppGrant): Boolean {
        val permission = permissionId(grant) ?: return false
        return bridge.grant_windows_open_settings(permission) == 1
    }

    private fun permissionId(grant: AppGrant): Int? = when (grant) {
        AppGrant.CAMERA -> 0
        AppGrant.MICROPHONE -> 1
        else -> null
    }
}
