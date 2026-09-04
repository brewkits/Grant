package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.js.mediaConstraints
import dev.brewkits.grant.js.navigatorJs
import dev.brewkits.grant.js.notificationJs
import dev.brewkits.grant.js.permissionDescriptor
import dev.brewkits.grant.utils.GrantLogger
import kotlinx.coroutines.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "WebGrantDelegate"

/**
 * Browser Platform Grant Delegate — shared by the `js` and `wasmJs` targets via `webMain`.
 *
 * Backed by the real browser consent APIs — `getUserMedia`, `Notification.requestPermission`,
 * `Geolocation.getCurrentPosition` — never a stub. [store] is unused: the browser itself is
 * the durable "have we asked before" record (a denied `getUserMedia` call stays denied across
 * page loads without any help from Grant), so there is nothing for a local store to add here.
 *
 * **Only four [AppGrant] values have a real browser equivalent**: [AppGrant.CAMERA],
 * [AppGrant.MICROPHONE], [AppGrant.LOCATION], [AppGrant.NOTIFICATION]. Every other value —
 * gallery, contacts, calendar, bluetooth, motion, and the rest — has no standard Web Platform
 * API to back it. Those resolve to [GrantStatus.DENIED_ALWAYS] with a logged reason, matching
 * the existing convention (`hasInfoPlistKey` on iOS) of failing safe and loud rather than
 * fabricating a status the platform cannot actually provide. [RawPermission] follows the same
 * rule: there is no generic "request an arbitrary permission" primitive in a browser.
 */
public actual class PlatformGrantDelegate(
    @Suppress("UNUSED_PARAMETER") private val store: GrantStore,
) {
    private var launcher: GrantLauncher? = null

    public actual fun setLauncher(launcher: GrantLauncher) {
        this.launcher = launcher
    }

    public actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
        if (grant !is AppGrant) return unsupported(grant.identifier, "checkStatus")
        return when (grant) {
            AppGrant.CAMERA -> queryStatus("camera")
            AppGrant.MICROPHONE -> queryStatus("microphone")
            AppGrant.LOCATION -> queryStatus("geolocation")
            AppGrant.NOTIFICATION -> notificationStatus()
            else -> unsupported(grant.identifier, "checkStatus")
        }
    }

    public actual suspend fun request(grant: GrantPermission): GrantStatus {
        if (grant !is AppGrant) return unsupported(grant.identifier, "request")
        return when (grant) {
            AppGrant.CAMERA -> requestMedia(video = true, audio = false)
            AppGrant.MICROPHONE -> requestMedia(video = false, audio = true)
            AppGrant.LOCATION -> requestLocation()
            AppGrant.NOTIFICATION -> requestNotification()
            else -> unsupported(grant.identifier, "request")
        }
    }

    public actual suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> =
        grants.associateWith { request(it) }

    /**
     * No standard way for a web page to open the browser's own site-permission settings —
     * unlike a native OS Settings app, there is no cross-browser URL or API for this. Logs
     * instead of silently doing nothing, so an integrator sees why nothing happened rather
     * than filing a "openSettings() is broken on web" bug.
     */
    public actual fun openSettings() {
        GrantLogger.w(
            TAG,
            "openSettings() has no browser equivalent. Guide the user to their browser's " +
                "site-permission UI yourself (e.g. the padlock icon next to the address bar); " +
                "there is no cross-browser API or URL scheme to do this programmatically.",
        )
    }

    private fun unsupported(identifier: String, op: String): GrantStatus {
        GrantLogger.w(
            TAG,
            "$op('$identifier'): no browser API backs this permission. Reporting " +
                "DENIED_ALWAYS rather than a status this platform cannot actually provide.",
        )
        return GrantStatus.DENIED_ALWAYS
    }

    /**
     * Permissions API status check. Wrapped in try/catch because Firefox throws a `TypeError`
     * for the "camera" and "microphone" permission names — that is a real, documented browser
     * inconsistency (MDN), not an error condition on Grant's part. A thrown/unsupported query
     * falls back to [GrantStatus.NOT_DETERMINED]: the real answer is "ask the browser by
     * actually requesting", which `request()` still does correctly regardless of whether
     * `query()` worked.
     */
    private suspend fun queryStatus(name: String): GrantStatus = try {
        val status = navigatorJs.permissions.query(permissionDescriptor(name)).await()
        mapPermissionState(status.state)
    } catch (e: Throwable) {
        GrantLogger.d(TAG, "permissions.query('$name') unsupported in this browser: ${e.message}")
        GrantStatus.NOT_DETERMINED
    }

    private fun mapPermissionState(state: String): GrantStatus = when (state) {
        "granted" -> GrantStatus.GRANTED
        "denied" -> GrantStatus.DENIED_ALWAYS
        else -> GrantStatus.NOT_DETERMINED // "prompt"
    }

    private fun notificationStatus(): GrantStatus = when (notificationJs.permission) {
        "granted" -> GrantStatus.GRANTED
        "denied" -> GrantStatus.DENIED_ALWAYS
        else -> GrantStatus.NOT_DETERMINED // "default"
    }

    /**
     * The actual camera/microphone consent prompt. A granted stream is stopped immediately —
     * Grant's contract is "resolve a status", not "hold the camera open" — and the browser
     * still remembers the grant for the next real `getUserMedia` call the app makes.
     */
    private suspend fun requestMedia(video: Boolean, audio: Boolean): GrantStatus = try {
        val stream = navigatorJs.mediaDevices.getUserMedia(mediaConstraints(video, audio)).await()
        for (track in stream.getTracks().toArray()) track.stop()
        GrantStatus.GRANTED
    } catch (e: Throwable) {
        GrantLogger.d(TAG, "getUserMedia denied or unavailable: ${e.message}")
        GrantStatus.DENIED_ALWAYS
    }

    private suspend fun requestNotification(): GrantStatus =
        mapNotificationResult(notificationJs.requestPermission().await().toString())

    private fun mapNotificationResult(result: String): GrantStatus = when (result) {
        "granted" -> GrantStatus.GRANTED
        "denied" -> GrantStatus.DENIED_ALWAYS
        else -> GrantStatus.NOT_DETERMINED
    }

    private suspend fun requestLocation(): GrantStatus = suspendCoroutine { cont ->
        navigatorJs.geolocation.getCurrentPosition(
            success = { cont.resume(GrantStatus.GRANTED) },
            error = { error ->
                val deniedByUser = error.code == 1
                cont.resume(if (deniedByUser) GrantStatus.DENIED_ALWAYS else GrantStatus.NOT_DETERMINED)
            },
        )
    }
}
