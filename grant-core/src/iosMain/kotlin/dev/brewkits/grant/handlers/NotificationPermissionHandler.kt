package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

private const val TAG = "NotificationPermissionHandler"

/**
 * Handles Push Notification permissions via UserNotifications framework.
 *
 * **Threading note:**
 * [UNUserNotificationCenter.getNotificationSettingsWithCompletionHandler] dispatches its
 * callback onto its own internal queue. Wrapping this call inside `runOnMain` would cause
 * nested `dispatch_async` leading to a potential deadlock when called from the main thread.
 * Both [checkStatus] and [request] therefore manage their own async dispatch via
 * [mainContinuation] and must NOT be wrapped in `runOnMain` by the caller.
 *
 * Correct denial status mapping:
 * Previously, [request] mapped `granted=false` directly to [GrantStatus.DENIED_ALWAYS].
 * This was incorrect because:
 * - The `granted` boolean conflates two distinct states (denied vs. error)
 * - After a denial, iOS notification permission IS permanent (cannot re-prompt), but
 *   re-reading [UNAuthorizationStatus] gives us the exact semantic state for better logging.
 * The fix re-reads `authorizationStatus` after the callback resolves, mirroring the
 * pattern used by [CalendarPermissionHandler] for the same reason.
 */
internal class NotificationPermissionHandler : PermissionHandler {

    @Deprecated(
        message = "checkStatus() for Notifications is always async on iOS. Use checkStatusAsync() instead.",
        level = DeprecationLevel.ERROR
    )
    override fun checkStatus(): GrantStatus {
        // Note: checkStatus for notifications is always async — this synchronous
        // overload is not supported. Callers should use the suspend version.
        // Returning NOT_DETERMINED as a safe default to avoid blocking the main thread.
        GrantLogger.w(TAG, "checkStatus() called synchronously for Notifications — use checkStatusAsync() instead.")
        return GrantStatus.NOT_DETERMINED
    }

    /**
     * Asynchronously reads the current notification authorization status.
     * This is the preferred way to check notification status on iOS.
     */
    suspend fun checkStatusAsync(): GrantStatus =
        withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { cont ->
                UNUserNotificationCenter.currentNotificationCenter()
                    .getNotificationSettingsWithCompletionHandler { settings ->
                        val result = mapAuthorizationStatus(settings?.authorizationStatus)
                        mainContinuation<GrantStatus> { s -> if (cont.isActive) cont.resume(s) }.invoke(result)
                    }
            }
        } ?: GrantStatus.NOT_DETERMINED

    /**
     * Re-reads `authorizationStatus` after the request callback rather than
     * relying on the callback's boolean (which can be flaky in some iOS versions). "denied" and
     * "error". The re-read gives the precise semantic state for correct [GrantStatus] mapping.
     */
    override suspend fun request(): GrantStatus =
        withTimeoutOrNull(60000L) {
            suspendCancellableCoroutine { cont ->
                val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(options) { _, error ->
                        if (error != null) {
                            GrantLogger.e(TAG, "Notification request error: ${error.localizedDescription}")
                        }
                        // Re-read actual status rather than trusting the `granted` boolean.
                        // On iOS, notification denial is permanent (cannot re-prompt), so the
                        // re-read gives us the exact system state.
                        UNUserNotificationCenter.currentNotificationCenter()
                            .getNotificationSettingsWithCompletionHandler { settings ->
                                val result = mapAuthorizationStatus(settings?.authorizationStatus)
                                mainContinuation<GrantStatus> { s -> if (cont.isActive) cont.resume(s) }.invoke(result)
                            }
                    }
            }
        } ?: GrantStatus.NOT_DETERMINED
}

// ────────────────────────────────────────────────────────────────────────────
// Internal helpers
// ────────────────────────────────────────────────────────────────────────────

private fun mapAuthorizationStatus(status: Long?): GrantStatus = when (status) {
    UNAuthorizationStatusAuthorized,
    UNAuthorizationStatusProvisional -> GrantStatus.GRANTED
    UNAuthorizationStatusDenied      -> GrantStatus.DENIED_ALWAYS
    UNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
    else                             -> GrantStatus.NOT_DETERMINED
}
