package dev.brewkits.grant.calendar

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.CalendarPermissionHandler

/**
 * Entry point for initializing the Grant Calendar module.
 */
actual object GrantCalendar {
    private var isInitialized = false

    /**
     * Registers the Calendar permission handler for iOS.
     */
    actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        IosPermissionHandlerRegistry.register(AppGrant.CALENDAR.identifier, CalendarPermissionHandler())
        IosPermissionHandlerRegistry.register(AppGrant.READ_CALENDAR.identifier, CalendarPermissionHandler())
    }
}
