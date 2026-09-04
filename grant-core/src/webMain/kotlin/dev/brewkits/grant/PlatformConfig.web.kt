package dev.brewkits.grant

/**
 * The browser has no rationale concept — `Permissions.query()` only reports "granted" /
 * "denied" / "prompt", with no OS-native "explain why, then re-ask" step the way Android's
 * `shouldShowRequestPermissionRationale` provides.
 */
internal actual object PlatformConfig {
    actual val isRationaleSupported: Boolean = false
}
