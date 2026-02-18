package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.runTest
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for all permission types supported by Grant.
 *
 * Ensures every AppGrant enum value is properly handled.
 */
@OptIn(ExperimentalNativeApi::class)
class AllPermissionTypesTest {

    private val manager = FakeGrantManager()

    @Test
    fun `CAMERA permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `MICROPHONE permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.MICROPHONE)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `GALLERY permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.GALLERY)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `GALLERY_IMAGES_ONLY permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.GALLERY_IMAGES_ONLY)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `GALLERY_VIDEO_ONLY permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.GALLERY_VIDEO_ONLY)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `LOCATION permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.LOCATION)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `LOCATION_ALWAYS permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.LOCATION_ALWAYS)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `NOTIFICATION permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.NOTIFICATION)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `BLUETOOTH permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.BLUETOOTH)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `CONTACTS permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.CONTACTS)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `CALENDAR permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.CALENDAR)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `MOTION permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.MOTION)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `STORAGE permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.STORAGE)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `SCHEDULE_EXACT_ALARM permission should be requestable`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED

        val result = manager.request(AppGrant.SCHEDULE_EXACT_ALARM)
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `All AppGrant enums should have unique identifiers`() {
        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY,
            AppGrant.LOCATION,
            AppGrant.LOCATION_ALWAYS,
            AppGrant.NOTIFICATION,
            AppGrant.BLUETOOTH,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.MOTION,
            AppGrant.STORAGE,
            AppGrant.SCHEDULE_EXACT_ALARM
        )

        val identifiers = grants.map { it.identifier }
        val uniqueIdentifiers = identifiers.toSet()

        assertEquals(grants.size, uniqueIdentifiers.size, "All grants should have unique identifiers")
    }

    @Test
    fun `All AppGrant enums should have non-empty identifiers`() {
        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY,
            AppGrant.LOCATION,
            AppGrant.LOCATION_ALWAYS,
            AppGrant.NOTIFICATION,
            AppGrant.BLUETOOTH,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.MOTION,
            AppGrant.STORAGE,
            AppGrant.SCHEDULE_EXACT_ALARM
        )

        grants.forEach { grant ->
            assertNotNull(grant.identifier, "Grant ${grant.name} should have identifier")
            assert(grant.identifier.isNotEmpty()) { "Grant ${grant.name} identifier should not be empty" }
        }
    }

    @Test
    fun `checkStatus should work for all permission types`() = runTest {
        manager.mockStatus = GrantStatus.GRANTED

        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY,
            AppGrant.LOCATION,
            AppGrant.LOCATION_ALWAYS,
            AppGrant.NOTIFICATION,
            AppGrant.BLUETOOTH,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.MOTION,
            AppGrant.STORAGE,
            AppGrant.SCHEDULE_EXACT_ALARM
        )

        grants.forEach { grant ->
            val status = manager.checkStatus(grant)
            assertEquals(GrantStatus.GRANTED, status, "Grant ${grant.name} checkStatus failed")
        }
    }

    @Test
    fun `request should work for all permission types when DENIED`() = runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.DENIED

        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.LOCATION
        )

        grants.forEach { grant ->
            val status = manager.request(grant)
            assertEquals(GrantStatus.DENIED, status, "Grant ${grant.name} request with DENIED failed")
        }
    }

    @Test
    fun `request should work for all permission types when DENIED_ALWAYS`() = runTest {
        manager.mockStatus = GrantStatus.DENIED_ALWAYS
        manager.mockRequestResult = GrantStatus.DENIED_ALWAYS

        val grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.LOCATION
        )

        grants.forEach { grant ->
            val status = manager.request(grant)
            assertEquals(GrantStatus.DENIED_ALWAYS, status, "Grant ${grant.name} request with DENIED_ALWAYS failed")
        }
    }
}
