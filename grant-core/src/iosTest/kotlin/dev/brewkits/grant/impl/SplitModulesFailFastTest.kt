package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.IosServiceRegistry
import dev.brewkits.grant.handlers.PermissionHandler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * grant-core without grant-camera / grant-gallery / grant-location (3.0.0, Issue #91).
 *
 * Nothing in grant-core's test binary registers these grants, which is exactly the
 * "upgraded but forgot the module" situation. Test binaries are debug binaries, so the
 * fail-fast branch is the one exercised here; the release branch (DENIED_ALWAYS + log) is the
 * same code path with `Platform.isDebugBinary` false.
 */
@OptIn(ExperimentalNativeApi::class)
class SplitModulesFailFastTest {

    private val delegate = PlatformGrantDelegate(InMemoryGrantStore())

    private val splitOut = listOf(
        AppGrant.CAMERA, AppGrant.MICROPHONE,
        AppGrant.GALLERY, AppGrant.GALLERY_IMAGES_ONLY, AppGrant.GALLERY_VIDEO_ONLY,
        AppGrant.GALLERY_ADD_ONLY, AppGrant.STORAGE,
        AppGrant.LOCATION,
    )

    @Test
    fun `test binary is a debug binary so the fail-fast branch is what runs here`() {
        assertTrue(Platform.isDebugBinary, "these tests assume a debug test binary")
    }

    @Test
    fun `checkStatus on a split-out grant without its module throws AssertionError in debug`() = runTest {
        splitOut.forEach { grant ->
            val error = assertFailsWith<AssertionError>("checkStatus($grant) must fail fast") {
                withTimeout(3_000L) { delegate.checkStatus(grant) }
            }
            assertTrue(
                error.message.orEmpty().contains("grant-"),
                "message must name the module to add, got: ${error.message}",
            )
        }
    }

    @Test
    fun `request on a split-out grant without its module throws AssertionError in debug`() = runTest {
        splitOut.forEach { grant ->
            assertFailsWith<AssertionError>("request($grant) must fail fast") {
                withTimeout(3_000L) { delegate.request(grant) }
            }
        }
    }

    /**
     * Must be an Error, not an Exception: GrantHandler wraps calls in `catch (e: Exception)`,
     * which would swallow an Exception and leave the developer with a silent DENIED_ALWAYS.
     */
    @Test
    fun `the fail-fast is not an Exception`() = runTest {
        val error = assertFailsWith<AssertionError> { delegate.checkStatus(AppGrant.CAMERA) }
        assertTrue((error as Throwable) !is Exception, "must escape catch (e: Exception)")
    }

    @Test
    fun `older opt-in modules keep log-and-deny without fail-fast`() = runTest {
        // Contacts were never in grant-core, so their behaviour is unchanged by 3.0.0.
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.CONTACTS) }
        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }

    @Test
    fun `a registered handler for a split-out grant is used instead of failing`() = runTest {
        val stub = object : PermissionHandler {
            override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(): GrantStatus = GrantStatus.GRANTED
        }
        IosPermissionHandlerRegistry.register(AppGrant.MICROPHONE.identifier, stub)
        try {
            assertEquals(GrantStatus.GRANTED, withTimeout(3_000L) { delegate.checkStatus(AppGrant.MICROPHONE) })
            assertEquals(GrantStatus.GRANTED, withTimeout(3_000L) { delegate.request(AppGrant.MICROPHONE) })
        } finally {
            IosPermissionHandlerRegistry.unregister(AppGrant.MICROPHONE.identifier)
        }
    }

    @Test
    fun `LOCATION_GPS without grant-location is UNKNOWN and never a guessed ENABLED`() = runTest {
        val status = withTimeout(3_000L) {
            ServiceFactory.createServiceManager().checkServiceStatus(ServiceType.LOCATION_GPS)
        }
        assertEquals(ServiceStatus.UNKNOWN, status)
    }

    @Test
    fun `LOCATION_GPS uses the checker a module registers`() = runTest {
        IosServiceRegistry.register(ServiceType.LOCATION_GPS) { ServiceStatus.DISABLED }
        try {
            val status = withTimeout(3_000L) {
                ServiceFactory.createServiceManager().checkServiceStatus(ServiceType.LOCATION_GPS)
            }
            assertEquals(ServiceStatus.DISABLED, status)
        } finally {
            IosServiceRegistry.unregister(ServiceType.LOCATION_GPS)
        }
    }
}
