package dev.brewkits.grant.demo

import dev.brewkits.grant.utils.GrantLogger

/**
 * Enables [GrantLogger]'s console output so the demo doubles as a live example of the
 * library's internal diagnostics — module-not-registered warnings, permanently-denied
 * grants, Settings-navigation failures, and similar edge cases. (Per-step funnel events
 * such as onRequested/onGranted/onDenied are a separate mechanism, [GrantEventListener],
 * not this one.) `GrantLogger.isEnabled` defaults to `false` — silent by default, opt-in
 * only.
 *
 * Exposed as a function of the demo's own module rather than called directly from Swift:
 * `grant-core` is not `export()`-ed from the demo's iOS framework, so `GrantLogger` itself
 * does not appear in the generated Obj-C header. Exporting it would grow the framework's
 * public Swift-visible surface just to reach one debug switch; a small function inside the
 * demo module's own compiled API avoids that.
 */
object DemoLogging {
    fun enableGrantLogging() {
        GrantLogger.isEnabled = true
    }
}
