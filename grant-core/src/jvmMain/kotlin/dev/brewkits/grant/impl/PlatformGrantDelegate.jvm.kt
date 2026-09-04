package dev.brewkits.grant.impl

import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.handlers.DesktopPermissionHandlerRegistry
import dev.brewkits.grant.utils.GrantLogger

private const val TAG = "JvmGrantDelegate"

/**
 * JVM desktop delegate — deliberately minimal. `grant-core` has no macOS/Windows framework
 * dependency and never will; every [GrantPermission] resolves through
 * [DesktopPermissionHandlerRegistry], and a lookup miss reports [GrantStatus.DENIED_ALWAYS]
 * with a logged reason rather than a fabricated [GrantStatus.GRANTED]. The `grant-desktop`
 * module (opt-in) is what actually populates the registry with a real macOS TCC bridge — see
 * `DesktopPermissionHandlerRegistry`'s KDoc and ROADMAP.md v2.6.0.
 *
 * [store] is unused here for the same reason it's unused on iOS/web: the OS's own privacy
 * database is the durable "have we asked before" record.
 */
public actual class PlatformGrantDelegate(
    @Suppress("UNUSED_PARAMETER") private val store: GrantStore,
) {
    private var launcher: GrantLauncher? = null

    public actual fun setLauncher(launcher: GrantLauncher) {
        this.launcher = launcher
    }

    public actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        val handler = DesktopPermissionHandlerRegistry.get(grant.identifier)
        if (handler == null) {
            unsupported(grant.identifier, "checkStatus")
            return GrantStatus.DENIED_ALWAYS
        }
        return handler.checkStatus()
    }

    public actual suspend fun request(grant: GrantPermission): GrantStatus {
        val handler = DesktopPermissionHandlerRegistry.get(grant.identifier)
        if (handler == null) {
            unsupported(grant.identifier, "request")
            return GrantStatus.DENIED_ALWAYS
        }
        return handler.request()
    }

    public actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> =
        grants.associateWith { request(it) }

    /**
     * Opens System Settings' Privacy & Security pane. Unlike the browser target, macOS *does*
     * have a real, documented URL scheme for this
     * (`x-apple.systempreferences:com.apple.preference.security`) — but only `grant-desktop`
     * can invoke it (it needs `NSWorkspace`, an AppKit/macOS-only API `grant-core`'s `jvmMain`
     * does not and should not depend on, since this module also has to compile on any JVM,
     * not just macOS). Logs instead of silently doing nothing until that module registers a
     * real handler.
     */
    public actual fun openSettings() {
        GrantLogger.w(
            TAG,
            "openSettings() has no effect without the grant-desktop module. Add it and call " +
                "GrantDesktop.initialize() to open System Settings' Privacy & Security pane.",
        )
    }

    private fun unsupported(identifier: String, op: String) {
        GrantLogger.w(
            TAG,
            "$op('$identifier'): no handler registered in DesktopPermissionHandlerRegistry. " +
                "Reporting DENIED_ALWAYS rather than a status this platform cannot actually " +
                "provide. Add the grant-desktop module and call GrantDesktop.initialize() for " +
                "real macOS support.",
        )
    }
}
