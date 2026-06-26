package dev.brewkits.grant

import android.app.Activity
import java.lang.ref.WeakReference

internal actual object PlatformConfig {
    actual val isRationaleSupported: Boolean = true

    private var activityRef: WeakReference<Activity>? = null

    /**
     * The current foreground Activity.
     * Used for accurate [GrantStatus.DENIED] vs [GrantStatus.DENIED_ALWAYS]
     * detection via `shouldShowRequestPermissionRationale`.
     *
     * Kept up to date automatically by `PlatformGrantDelegate`'s `Application.ActivityLifecycleCallbacks`
     * registration — apps and tests do not need to set this themselves outside of test fakes.
     */
    var activity: Activity?
        get() = activityRef?.get()?.takeIf { !it.isDestroyed && !it.isFinishing }
        set(value) {
            activityRef = value?.let { WeakReference(it) }
        }
}
