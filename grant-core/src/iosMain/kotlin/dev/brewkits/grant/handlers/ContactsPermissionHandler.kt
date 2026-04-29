package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.hasInfoPlistKey
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNAuthorizationStatusDenied
import platform.Contacts.CNAuthorizationStatusNotDetermined
import platform.Contacts.CNAuthorizationStatusRestricted
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import kotlin.coroutines.resume

private const val TAG = "ContactsPermissionHandler"

/**
 * Handles Contacts permissions via the Contacts framework.
 *
 * **Why Contacts is isolated here:**
 * Linking the Contacts framework causes Apple to require NSContactsUsageDescription
 * in Info.plist. Isolating this import prevents that for apps that don't use contacts.
 */
internal class ContactsPermissionHandler : IosPermissionHandler {

    override fun checkStatus(): GrantStatus {
        if (!hasInfoPlistKey(TAG, "NSContactsUsageDescription")) return GrantStatus.DENIED_ALWAYS
        return when (CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)) {
            CNAuthorizationStatusAuthorized    -> GrantStatus.GRANTED
            CNAuthorizationStatusDenied,
            CNAuthorizationStatusRestricted    -> GrantStatus.DENIED_ALWAYS
            CNAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
            else                               -> GrantStatus.NOT_DETERMINED
        }
    }

    override suspend fun request(): GrantStatus {
        if (!hasInfoPlistKey(TAG, "NSContactsUsageDescription")) return GrantStatus.DENIED_ALWAYS
        return suspendCancellableCoroutine { cont ->
            CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { granted, error ->
                if (error != null) {
                    GrantLogger.e(TAG, "Contacts request error: ${error.localizedDescription}")
                }
                mainContinuation<Boolean> { g ->
                    cont.resume(if (g) GrantStatus.GRANTED else GrantStatus.DENIED_ALWAYS)
                }.invoke(granted)
            }
        }
    }
}
