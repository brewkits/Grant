package dev.brewkits.grant

/**
 * Like TCC on iOS, macOS's privacy subsystem has no "explain why, then re-ask" concept
 * comparable to Android's `shouldShowRequestPermissionRationale` — a request is either
 * NotDetermined (will prompt), or already resolved (won't prompt again; the user must use
 * System Settings).
 */
internal actual object PlatformConfig {
    actual val isRationaleSupported: Boolean = false
}
