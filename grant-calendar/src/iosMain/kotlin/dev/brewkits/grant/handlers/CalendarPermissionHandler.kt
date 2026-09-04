package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.EventKit.EKAuthorizationStatus
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import platform.Foundation.NSBundle
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
internal class CalendarPermissionHandler : PermissionHandler {

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

/**
 * Any one of `NSCalendarsUsageDescription` (legacy),
 * `NSCalendarsFullAccessUsageDescription` or `NSCalendarsWriteOnlyAccessUsageDescription`
 * (both iOS 17+) is sufficient — an app that ships only one of them is correctly configured
 * and must not be treated as missing the others.
 *
 * The write-only key matters and was missing until now: iOS 17 split calendar access into
 * full and write-only, and an app that only adds events is *encouraged* to declare just the
 * write-only key. Rejecting that configuration reported DENIED_ALWAYS before EventKit was
 * ever consulted, for an app that was correctly set up. (The status mapping below already
 * handled the runtime side — `EKAuthorizationStatusWriteOnly` → PARTIAL_GRANTED — so only
 * this gate was wrong.) Confirmed against the iOS 26.5 runtime, which defines all three keys.
 *
 * Checks the keys silently (unlike [dev.brewkits.grant.utils.hasInfoPlistKey], which logs
 * an error for the one key it was asked about) and logs a single, accurate error only when
 * *neither* is present — the actual failure condition. Logging per-key here would report
 * "MISSING NSCalendarsFullAccessUsageDescription... returning DENIED_ALWAYS" even when the
 * legacy key covers the app and the real result is not DENIED_ALWAYS at all, which is a
 * false alarm a caller watching [GrantLogger] would reasonably chase as a bug.
 */
private fun hasAnyCalendarPlistKey(): Boolean {
    val hasLegacy = NSBundle.mainBundle.objectForInfoDictionaryKey("NSCalendarsUsageDescription") != null
    val hasFull = NSBundle.mainBundle.objectForInfoDictionaryKey("NSCalendarsFullAccessUsageDescription") != null
    val hasWriteOnly =
        NSBundle.mainBundle.objectForInfoDictionaryKey("NSCalendarsWriteOnlyAccessUsageDescription") != null
    return evaluateCalendarPlistKeys(hasLegacy, hasFull, hasWriteOnly)
}

/**
 * The decision logic on its own, independent of [NSBundle] — pulled out so a test can drive
 * every (hasLegacy, hasFull, hasWriteOnly) combination directly instead of depending on what the test
 * bundle's real `Info.plist` happens to contain. `internal` rather than `private` so
 * [dev.brewkits.grant.handlers.CalendarPlistLoggingTest] can reach it.
 */
internal fun evaluateCalendarPlistKeys(
    hasLegacy: Boolean,
    hasFull: Boolean,
    hasWriteOnly: Boolean = false,
): Boolean {
    if (!hasLegacy && !hasFull && !hasWriteOnly) {
        GrantLogger.e(
            TAG,
            "MISSING Info.plist key: need one of 'NSCalendarsUsageDescription', " +
                "'NSCalendarsFullAccessUsageDescription' or " +
                "'NSCalendarsWriteOnlyAccessUsageDescription'. Add one with a usage " +
                "description to prevent crashes. Returning DENIED_ALWAYS as a safety fallback.",
        )
    }
    return hasLegacy || hasFull || hasWriteOnly
}
