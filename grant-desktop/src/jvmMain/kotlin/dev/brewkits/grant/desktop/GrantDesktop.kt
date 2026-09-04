package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.DesktopPermissionHandlerRegistry
import dev.brewkits.grant.utils.GrantLogger

private const val TAG = "GrantDesktop"

/**
 * Entry point for the `grant-desktop` module — mirrors `GrantContacts`/`GrantCalendar`/
 * `GrantMotion`'s `initialize()` pattern on iOS. Call this once, e.g. at your Compose Desktop
 * app's `main()`, before any [dev.brewkits.grant.GrantManager] call for a desktop permission.
 *
 * A consumer that adds this module but runs on a non-macOS JVM (or on an architecture with no
 * bundled dylib — see `NativeBridgeLoader`) gets a logged notice and every desktop
 * [AppGrant] still resolves to `DENIED_ALWAYS` via `grant-core`'s own fallback delegate; this
 * module never fabricates a `GRANTED` for a permission it has no real handler for.
 *
 * **Tier 2 status (ROADMAP.md v2.6.0): camera and microphone, plus Settings.** Location,
 * contacts, and calendar are planned but not yet wired — see the roadmap for why camera was
 * verified first (a harness had to exist before any handler could be trusted) and what's next.
 */
public object GrantDesktop {
    private var isInitialized = false

    /**
     * `@Synchronized` rather than the plain flag `GrantContacts.initialize()` uses on iOS: two
     * threads calling this concurrently on a JVM could both pass an unguarded check and
     * double-register. Registration is idempotent so the damage would be limited, but the guard
     * is what makes "called once" a fact rather than a hope.
     */
    @Synchronized
    public fun initialize() {
        if (isInitialized) return
        isInitialized = true

        val bridge = NativeBridgeLoader.library
        if (bridge == null) {
            GrantLogger.w(
                TAG,
                "grant-desktop has no active macOS bridge on this JVM " +
                    "(os.name=${System.getProperty("os.name")}, os.arch=${System.getProperty("os.arch")}). " +
                    "Desktop permissions will report DENIED_ALWAYS via grant-core's fallback delegate.",
            )
            return
        }

        DesktopPermissionHandlerRegistry.register(AppGrant.CAMERA.identifier, AVMediaPermissionHandler.camera(bridge))
        DesktopPermissionHandlerRegistry.register(AppGrant.MICROPHONE.identifier, AVMediaPermissionHandler.microphone(bridge))
        // openSettings() has no per-permission target on macOS worth distinguishing here —
        // System Settings' Privacy pane always opens to Camera; a real per-permission deep
        // link (Microphone, Contacts, ...) is a documented follow-up (ROADMAP.md v2.6.0), not
        // a silent limitation — it's stated in the KDoc on grant_open_privacy_settings().
        DesktopPermissionHandlerRegistry.settingsOpener = { bridge.grant_open_privacy_settings("Privacy_Camera") }

        GrantLogger.i(TAG, "grant-desktop initialized with the real macOS bridge (camera, microphone, settings).")
    }
}
