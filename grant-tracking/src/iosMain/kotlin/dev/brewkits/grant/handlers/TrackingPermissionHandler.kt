package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AppTrackingTransparency.ATTrackingManager
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatus
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusAuthorized
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusDenied
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusNotDetermined
import platform.AppTrackingTransparency.ATTrackingManagerAuthorizationStatusRestricted
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import kotlin.coroutines.resume

private const val TAG = "TrackingPermissionHandler"
private const val USAGE_KEY = "NSUserTrackingUsageDescription"

/**
 * Handles App Tracking Transparency via `ATTrackingManager`.
 *
 * **Why ATT is isolated in its own module:**
 * Linking `AppTrackingTransparency.framework` makes Apple require
 * [USAGE_KEY] in Info.plist for *every* app that links it — including apps that never track.
 * Keeping it out of `grant-core` is the same isolation `grant-contacts`, `grant-calendar` and
 * `grant-motion` exist for (Issues #38, #45).
 *
 * **Both denial states map to [GrantStatus.DENIED_ALWAYS], but they are not the same thing**,
 * and the log says which one it is because the remedy differs:
 * - `denied` — this user declined for this app. Recoverable in Settings → *this app* →
 *   Allow Tracking.
 * - `restricted` — the device-level "Allow Apps to Request to Track" switch is off, or a
 *   configuration profile forbids it. The prompt will not appear at all, and the user cannot
 *   change it from the app's own settings page. Sending them to app settings would be a dead
 *   end, so the log points at the right screen instead.
 */
internal class TrackingPermissionHandler : PermissionHandler {

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, USAGE_KEY)) return GrantStatus.DENIED_ALWAYS
        return mapStatus(ATTrackingManager.trackingAuthorizationStatus)
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, USAGE_KEY)) return GrantStatus.DENIED_ALWAYS

        warnIfNotActive()

        return suspendCancellableCoroutine { cont ->
            ATTrackingManager.requestTrackingAuthorizationWithCompletionHandler { _ ->
                // Re-read the real status rather than trusting the callback's raw value — the
                // same convention the Contacts and Calendar handlers already follow.
                mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(checkStatus())
            }
        }
    }

    /**
     * iOS only presents the ATT prompt while the app is foreground-**active**. Called during
     * launch, from the background, or behind a modal transition, the completion handler fires
     * immediately with the *current* status and no prompt is shown — and because the status is
     * then still `notDetermined`, this is easy to mistake for "the user dismissed it".
     *
     * There is exactly one ask per install, so a request spent this way is spent for good.
     * Warning is the honest thing to do: Grant cannot defer the call on the app's behalf
     * without guessing when a good moment is, and guessing wrong would burn the ask too.
     */
    private fun warnIfNotActive() {
        val state = UIApplication.sharedApplication.applicationState
        if (state != UIApplicationState.UIApplicationStateActive) {
            GrantLogger.w(
                TAG,
                "requestTrackingAuthorization() called while the app is not foreground-active " +
                    "(state=$state). iOS will not show the prompt and will return the current " +
                    "status immediately — and the one ask this install gets is spent. Request " +
                    "it from a screen the user is looking at.",
            )
        }
    }

    private fun mapStatus(raw: ATTrackingManagerAuthorizationStatus): GrantStatus = when (raw) {
        ATTrackingManagerAuthorizationStatusAuthorized -> GrantStatus.GRANTED
        ATTrackingManagerAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
        ATTrackingManagerAuthorizationStatusDenied -> {
            GrantLogger.d(TAG, "Tracking denied by this user for this app — recoverable in Settings > (this app) > Allow Tracking.")
            GrantStatus.DENIED_ALWAYS
        }
        ATTrackingManagerAuthorizationStatusRestricted -> {
            GrantLogger.d(
                TAG,
                "Tracking is restricted device-wide (Settings > Privacy & Security > Tracking > " +
                    "Allow Apps to Request to Track is off, or an MDM profile forbids it). The " +
                    "prompt will not appear and this app's own settings page cannot change it.",
            )
            GrantStatus.DENIED_ALWAYS
        }
        else -> GrantStatus.NOT_DETERMINED
    }
}
