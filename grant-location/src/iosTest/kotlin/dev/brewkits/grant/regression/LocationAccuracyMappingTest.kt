package dev.brewkits.grant.regression

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.handlers.mapLocationStatus
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D3: iOS "Precise Location: Off" must surface as PARTIAL_GRANTED, matching Android's
 * "Approximate" choice, so shared code sees the same status on both platforms.
 */
class LocationAccuracyMappingTest {

    private val full = 0L
    private val reduced = 1L

    @Test
    fun `when in use with reduced accuracy is PARTIAL_GRANTED`() =
        assertEquals(GrantStatus.PARTIAL_GRANTED, mapLocationStatus(kCLAuthorizationStatusAuthorizedWhenInUse, reduced))

    @Test
    fun `always with reduced accuracy is PARTIAL_GRANTED`() =
        assertEquals(GrantStatus.PARTIAL_GRANTED, mapLocationStatus(kCLAuthorizationStatusAuthorizedAlways, reduced))

    @Test
    fun `when in use with full accuracy is GRANTED`() =
        assertEquals(GrantStatus.GRANTED, mapLocationStatus(kCLAuthorizationStatusAuthorizedWhenInUse, full))

    @Test
    fun `always with full accuracy is GRANTED`() =
        assertEquals(GrantStatus.GRANTED, mapLocationStatus(kCLAuthorizationStatusAuthorizedAlways, full))

    @Test
    fun `accuracy is irrelevant when location is not authorised`() {
        assertEquals(GrantStatus.DENIED_ALWAYS, mapLocationStatus(kCLAuthorizationStatusDenied, reduced))
        assertEquals(GrantStatus.DENIED_ALWAYS, mapLocationStatus(kCLAuthorizationStatusRestricted, reduced))
        assertEquals(GrantStatus.NOT_DETERMINED, mapLocationStatus(kCLAuthorizationStatusNotDetermined, reduced))
    }
}
