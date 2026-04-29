package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.EventKit.EKAuthorizationStatus
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import kotlin.coroutines.resume

private const val TAG = "CalendarPermissionHandler"

/**
 * Handles Calendar permissions via EventKit framework.
 *
 * **Why EventKit is isolated here:**
 * Linking EventKit causes Apple to require NSCalendarsUsageDescription (or
 * NSCalendarsFullAccessUsageDescription on iOS 17+) in Info.plist, even if the
 * app never requests calendar access. Isolating this import prevents that.
 *
 * **iOS 17+ note:**
 * Apple introduced two new `EKAuthorizationStatus` values that are not yet
 * present in the Kotlin/Native cinterop bindings:
 * - `EKAuthorizationStatusFullAccess`  (= [EK_STATUS_FULL_ACCESS])  → [GrantStatus.GRANTED]
 * - `EKAuthorizationStatusWriteOnly`   (= [EK_STATUS_WRITE_ONLY])   → [GrantStatus.PARTIAL_GRANTED]
 *
 * Magic numbers replaced with named constants (see companion object).
 *
 * **Why the `granted` boolean is ignored in [request]:**
 * On iOS 17+, the `requestAccessToEntityType` callback returns `granted=false`
 * for both "Don't Allow" and "Add Events Only" (write-only). We therefore
 * re-read the raw authorization status after the dialog dismisses to distinguish
 * the two cases correctly.
 */
internal class CalendarPermissionHandler : IosPermissionHandler {

    private companion object {
        // Named constants for iOS 17+ EKAuthorizationStatus values that are not
        // yet present in the Kotlin/Native platform headers.
        // Source: https://developer.apple.com/documentation/eventkit/ekauthorizationstatus
        // Verified against iOS 17.0 SDK: EKAuthorizationStatusFullAccess = 3, WriteOnly = 4.
        const val EK_STATUS_FULL_ACCESS: EKAuthorizationStatus = 3L
        const val EK_STATUS_WRITE_ONLY: EKAuthorizationStatus  = 4L
    }

    override fun checkStatus(): GrantStatus {
        if (!hasAnyCalendarPlistKey()) return GrantStatus.DENIED_ALWAYS
        return mapEKStatus(EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent))
    }

    override suspend fun request(): GrantStatus {
        if (!hasAnyCalendarPlistKey()) return GrantStatus.DENIED_ALWAYS
        return suspendCancellableCoroutine { cont ->
            val eventStore = EKEventStore()
            eventStore.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { _, error ->
                if (error != null) {
                    GrantLogger.e(TAG, "Calendar request error: ${error.localizedDescription}")
                }
                // Re-read actual status — do NOT trust the `granted` boolean on iOS 17+.
                val result = mapEKStatus(
                    EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
                )
                mainContinuation<GrantStatus> { s -> cont.resume(s) }.invoke(result)
            }
        }
    }

    private fun mapEKStatus(rawStatus: EKAuthorizationStatus): GrantStatus = when (rawStatus) {
        EKAuthorizationStatusAuthorized    -> GrantStatus.GRANTED          // iOS < 17
        EKAuthorizationStatusDenied,
        EKAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
        EKAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
        EK_STATUS_FULL_ACCESS              -> GrantStatus.GRANTED          // iOS 17+
        EK_STATUS_WRITE_ONLY               -> GrantStatus.PARTIAL_GRANTED  // iOS 17+
        else                               -> GrantStatus.NOT_DETERMINED
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Internal helpers
// ────────────────────────────────────────────────────────────────────────────

private fun hasAnyCalendarPlistKey(): Boolean {
    val hasLegacy = hasInfoPlistKey(TAG, "NSCalendarsUsageDescription")
    val hasFull   = hasInfoPlistKey(TAG, "NSCalendarsFullAccessUsageDescription")
    // Either key is sufficient. hasInfoPlistKey already logs the error if missing.
    return hasLegacy || hasFull
}
