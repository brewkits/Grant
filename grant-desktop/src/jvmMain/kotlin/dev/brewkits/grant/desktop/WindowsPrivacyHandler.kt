package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.handlers.DesktopPermissionHandler
import dev.brewkits.grant.utils.GrantLogger

private const val TAG = "WindowsPrivacyHandler"

/**
 * Exposes Windows' camera/microphone privacy state through [dev.brewkits.grant.GrantManager].
 *
 * **Read-only by design.** Windows offers no API to show a consent prompt for a desktop process,
 * so [request] cannot ask anything: it reports the current state and tells the caller what to
 * do next. Without this handler `GrantManager.checkStatus(CAMERA)` on Windows reported
 * DENIED_ALWAYS even when access was allowed, because only a separate, non-`GrantStatus`
 * API ([GrantDesktop.checkWindowsPrivacyStatus]) knew the real answer.
 */
internal class WindowsPrivacyHandler(
    private val client: WindowsPrivacyClient,
    private val grant: AppGrant,
) : DesktopPermissionHandler {

    override fun checkStatus(): GrantStatus = toGrantStatus(client.checkStatus(grant))

    override suspend fun request(): GrantStatus {
        val status = checkStatus()
        if (status != GrantStatus.GRANTED) {
            GrantLogger.i(
                TAG,
                "Windows has no consent dialog for '${grant.identifier}' (status $status). " +
                    "Direct the user to Settings with GrantDesktop.openSettings(${grant.name}).",
            )
        }
        return status
    }

    internal companion object {
        /**
         * Windows only ever reports `Unspecified` for "never decided", so it maps to
         * NOT_DETERMINED. An unavailable bridge or failed query is not a grant and not a refusal
         * Grant can prove, but the platform cannot back a better answer, so it is reported as
         * DENIED_ALWAYS with a log rather than a fabricated GRANTED.
         */
        fun toGrantStatus(status: WindowsPrivacyStatus): GrantStatus = when (status) {
            WindowsPrivacyStatus.ALLOWED -> GrantStatus.GRANTED
            WindowsPrivacyStatus.DENIED_BY_USER,
            WindowsPrivacyStatus.DENIED_BY_SYSTEM -> GrantStatus.DENIED_ALWAYS
            WindowsPrivacyStatus.UNSPECIFIED -> GrantStatus.NOT_DETERMINED
            WindowsPrivacyStatus.UNAVAILABLE -> {
                GrantLogger.w(TAG, "Windows privacy state is unavailable; reporting DENIED_ALWAYS.")
                GrantStatus.DENIED_ALWAYS
            }
        }
    }
}
