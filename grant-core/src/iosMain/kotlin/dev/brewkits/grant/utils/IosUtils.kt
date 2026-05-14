package dev.brewkits.grant.utils

import platform.Foundation.NSBundle

/**
 * Shared iOS utility: validates that a required `Info.plist` key is present.
 *
 * Called before every native permission API to prevent SIGABRT crashes caused by
 * missing usage description keys. Returns `false` and logs an error if the key
 * is absent, allowing callers to return [GrantStatus.DENIED_ALWAYS] safely.
 *
 * **DRY fix (Issue #6):** Replaces the 7 identical file-private copies that
 * previously existed across each handler file.
 *
 * @param tag   Log tag of the calling handler (for error attribution).
 * @param key   The Info.plist key to validate (e.g. "NSCameraUsageDescription").
 * @return `true` if the key exists and is non-null, `false` otherwise.
 */
fun hasInfoPlistKey(tag: String, key: String): Boolean {
    val value = NSBundle.mainBundle.objectForInfoDictionaryKey(key)
    if (value == null) {
        GrantLogger.e(
            tag,
            "MISSING Info.plist key: '$key'. " +
            "Add this key with a usage description to prevent crashes. " +
            "Returning DENIED_ALWAYS as a safety fallback."
        )
    }
    return value != null
}
