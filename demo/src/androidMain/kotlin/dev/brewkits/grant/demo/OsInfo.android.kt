package dev.brewkits.grant.demo

import android.os.Build

actual object OsInfo {
    actual val platform: String = "Android"

    actual val osVersion: String
        get() {
            val release = Build.VERSION.RELEASE
            val codename = versionCodename(Build.VERSION.SDK_INT)
            return "Android $release ($codename)"
        }

    actual val apiLevel: String
        get() = "API ${Build.VERSION.SDK_INT}"

    actual fun galleryBehaviorNote(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            "API ${Build.VERSION.SDK_INT}: Granular media + Partial access (SELECT PHOTOS)"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            "API ${Build.VERSION.SDK_INT}: READ_MEDIA_IMAGES + READ_MEDIA_VIDEO"
        else ->
            "API ${Build.VERSION.SDK_INT}: READ_EXTERNAL_STORAGE (legacy)"
    }

    actual fun notificationBehaviorNote(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            "API ${Build.VERSION.SDK_INT}: POST_NOTIFICATIONS required (runtime)"
        else
            "API ${Build.VERSION.SDK_INT}: Auto-granted at install time"

    actual fun motionBehaviorNote(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            "API ${Build.VERSION.SDK_INT}: ACTIVITY_RECOGNITION (runtime)"
        else
            "API ${Build.VERSION.SDK_INT}: GMS ACTIVITY_RECOGNITION fallback"

    private fun versionCodename(sdk: Int): String = when {
        sdk >= 35 -> "Android 15 / VanillaIceCream"
        sdk >= 34 -> "Android 14 / UpsideDownCake"
        sdk >= 33 -> "Android 13 / Tiramisu"
        sdk >= 32 -> "Android 12L / SV2"
        sdk >= 31 -> "Android 12 / S"
        sdk >= 30 -> "Android 11 / R"
        sdk >= 29 -> "Android 10 / Q"
        sdk >= 28 -> "Android 9 / Pie"
        sdk >= 26 -> "Android 8 / Oreo"
        sdk >= 23 -> "Android 6+ / M"
        else -> "Legacy (< Android 6)"
    }
}
