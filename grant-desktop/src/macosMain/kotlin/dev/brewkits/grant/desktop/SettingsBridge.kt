@file:Suppress("unused") // called from the JVM side over JNA, not from Kotlin/Native code

package dev.brewkits.grant.desktop

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSWorkspace
import platform.Foundation.NSURL

/**
 * Opens System Settings' Privacy & Security → Camera pane via the documented
 * `x-apple.systempreferences:` URL scheme — the real, Apple-sanctioned mechanism macOS
 * provides for this (unlike the browser target, which has no equivalent at all; see
 * `PlatformGrantDelegate.web.kt`). Only `grant-desktop` can call this: it needs `NSWorkspace`
 * (AppKit), which `grant-core`'s `jvmMain` deliberately does not depend on, since that module
 * also has to compile on any JVM, not just macOS.
 *
 * Not permission-specific by URL — `com.apple.preference.security?Privacy_Camera` deep-links
 * to the Camera row specifically, but the same mechanism covers every `Privacy_*` anchor
 * (Microphone, Contacts, Calendars, ...). One exported function, not one per permission.
 */
@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
@CName("grant_open_privacy_settings")
public fun grantOpenPrivacySettings(anchor: String): Boolean {
    val url = NSURL(string = "x-apple.systempreferences:com.apple.preference.security?$anchor")
    return NSWorkspace.sharedWorkspace.openURL(url)
}
