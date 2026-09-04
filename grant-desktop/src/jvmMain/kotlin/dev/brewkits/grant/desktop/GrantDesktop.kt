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
 * **Tier 2 status (ROADMAP.md v2.6.0): camera only.** Microphone, location, contacts, and
 * calendar are planned but not yet wired — see the roadmap for why camera was verified first
 * (a harness had to exist before any handler could be trusted) and what's next.
 */
public object GrantDesktop {
    private var isInitialized = false

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

        DesktopPermissionHandlerRegistry.register(AppGrant.CAMERA.identifier, CameraPermissionHandler(bridge))
        GrantLogger.i(TAG, "grant-desktop initialized with the real macOS bridge (camera).")
    }
}
