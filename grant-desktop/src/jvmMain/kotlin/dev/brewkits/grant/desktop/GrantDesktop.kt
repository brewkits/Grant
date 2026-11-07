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
 * macOS Apple Silicon registers camera/microphone permission handlers. Windows x64 registers
 * read-only camera/microphone handlers (status through `GrantManager`; Windows has no consent
 * dialog, so `request()` only reports the state) and also offers [checkWindowsPrivacyStatus] and
 * [openSettings].
 * Unsupported hosts/permissions retain core's DENIED_ALWAYS fallback. No permission is
 * fabricated as GRANTED when a handler or bridge is unavailable.
 *
 * **Tier 2 status (ROADMAP.md v2.6.0): camera and microphone, plus Settings.** Location,
 * contacts, and calendar are planned but not yet wired — see the roadmap for why camera was
 * verified first (a harness had to exist before any handler could be trusted) and what's next.
 */
public object GrantDesktop {
    private var isInitialized = false

    /**
     * Reads camera/microphone device-class privacy access on Windows x64 without a prompt.
     * This does not capture media, check hardware presence, or request authorization.
     * Other hosts, unsupported permissions and failed queries return UNAVAILABLE.
     */
    public fun checkWindowsPrivacyStatus(grant: AppGrant): WindowsPrivacyStatus =
        WindowsNativeBridge.library?.let { WindowsPrivacyClient(it).checkStatus(grant) }
            ?: WindowsPrivacyStatus.UNAVAILABLE

    /**
     * Opens this permission's privacy settings on Windows x64 or macOS Apple Silicon.
     * Returns whether the OS accepted the launch, not whether the user changed access.
     * Only camera and microphone are supported. No permission is granted by this call.
     */
    public fun openSettings(grant: AppGrant): Boolean {
        if (grant != AppGrant.CAMERA && grant != AppGrant.MICROPHONE) return false
        WindowsNativeBridge.library?.let { return WindowsPrivacyClient(it).openSettings(grant) }
        val anchor = if (grant == AppGrant.CAMERA) "Privacy_Camera" else "Privacy_Microphone"
        return NativeBridgeLoader.library?.grant_open_privacy_settings(anchor) ?: false
    }

    /**
     * Whether this module can handle [grant] on the current JVM with its native bridge loaded.
     * This checks native permission-request support, not authorization or device availability.
     * Windows privacy checks use [checkWindowsPrivacyStatus]; this method remains false there.
     * Call [initialize] before using GrantManager to request a supported permission.
     */
    public fun isSupported(grant: AppGrant): Boolean =
        (grant == AppGrant.CAMERA || grant == AppGrant.MICROPHONE) && NativeBridgeLoader.library != null

    /** Registers the read-only Windows handlers; separate so it can be tested without a Windows host. */
    internal fun registerWindowsHandlers(client: WindowsPrivacyClient) {
        listOf(AppGrant.CAMERA, AppGrant.MICROPHONE).forEach {
            DesktopPermissionHandlerRegistry.register(it.identifier, WindowsPrivacyHandler(client, it))
        }
    }

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

        val windowsBridge = WindowsNativeBridge.library
        if (windowsBridge != null) {
            registerWindowsHandlers(WindowsPrivacyClient(windowsBridge))
            DesktopPermissionHandlerRegistry.settingsOpener = { openSettings(AppGrant.CAMERA) }
            GrantLogger.i(
                TAG,
                "Windows privacy state is available through GrantManager (read-only: Windows has no " +
                    "consent dialog, so request() reports the state; use openSettings(grant) to guide the user).",
            )
            return
        }

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
        DesktopPermissionHandlerRegistry.register(
            AppGrant.MICROPHONE.identifier,
            AVMediaPermissionHandler.microphone(bridge),
        )
        // openSettings() has no per-permission target on macOS worth distinguishing here —
        // System Settings' Privacy pane always opens to Camera; a real per-permission deep
        // link (Microphone, Contacts, ...) is a documented follow-up (ROADMAP.md v2.6.0), not
        // a silent limitation — it's stated in the KDoc on grant_open_privacy_settings().
        DesktopPermissionHandlerRegistry.settingsOpener = { bridge.grant_open_privacy_settings("Privacy_Camera") }

        GrantLogger.i(TAG, "grant-desktop initialized with the real macOS bridge (camera, microphone, settings).")
    }
}
