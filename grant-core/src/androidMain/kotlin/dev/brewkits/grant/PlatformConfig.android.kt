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
     */
    var activity: Activity?
        get() = activityRef?.get()?.takeIf { !it.isDestroyed && !it.isFinishing }
        set(value) {
            activityRef = value?.let { WeakReference(it) }
        }
}
