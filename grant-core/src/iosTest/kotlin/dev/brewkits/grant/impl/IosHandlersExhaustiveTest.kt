package dev.brewkits.grant.impl

import dev.brewkits.grant.*
import dev.brewkits.grant.testing.FakeGrantStore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Exhaustive coverage test for all iOS Handlers via PlatformGrantDelegate.
 * This ensures the dispatch logic (when-expression) and handler registration 
 * are fully covered.
 */
class IosHandlersExhaustiveTest {

    private lateinit var store: FakeGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        store = FakeGrantStore()
        delegate = PlatformGrantDelegate(store)
    }

    @Test
    fun testDispatchAllAppGrants() = runTest {
        val allAppGrants = AppGrant.values()
        
        // Handled by grant-camera / grant-gallery / grant-location since 3.0.0. Unregistered in
        // this binary, they must fail fast with AssertionError; anything else must return a status.
        val splitOut = setOf(
            AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.GALLERY, AppGrant.GALLERY_IMAGES_ONLY,
            AppGrant.GALLERY_VIDEO_ONLY, AppGrant.GALLERY_ADD_ONLY, AppGrant.STORAGE, AppGrant.LOCATION,
        )

        allAppGrants.forEach { grant ->
            if (grant == AppGrant.NOTIFICATION) return@forEach
            if (grant in splitOut) {
                assertFailsWith<AssertionError>("$grant without its module must fail fast") {
                    delegate.checkStatus(grant)
                }
                return@forEach
            }
            try {
                val status = delegate.checkStatus(grant)
                assertNotNull(status, "Status for $grant should not be null")
            } catch (e: Exception) {
                // Re-throw genuine crashes. Ignore specific simulator limits if absolutely necessary.
                if (e.message?.contains("bundleProxyForCurrentProcess") == true) {
                    // known simulator limitation for some framework calls
                } else {
                    throw e
                }
            }
        }
    }

    @Test
    fun testSpecificHandlers() = runTest {
        val targetGrants = listOf(
            AppGrant.LOCATION_ALWAYS,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.MOTION,
            AppGrant.BLUETOOTH
        )

        targetGrants.forEach { grant ->
            try {
                val status = delegate.checkStatus(grant)
                assertNotNull(status, "Status for $grant should not be null")
            } catch (e: Exception) {
                if (e.message?.contains("bundleProxyForCurrentProcess") != true) {
                    throw e
                }
            }
        }
    }

    // testGalleryVariants moved with the gallery handler: grant-gallery's GrantGalleryIosTest.

    @Test
    fun testBluetoothVariants() = runTest {
        assertNotNull(delegate.checkStatus(AppGrant.BLUETOOTH_ADVERTISE))
    }

    @Test
    fun testContactsAndCalendarVariants() = runTest {
        try {
            assertNotNull(delegate.checkStatus(AppGrant.READ_CONTACTS))
            assertNotNull(delegate.checkStatus(AppGrant.READ_CALENDAR))
        } catch (e: Exception) {
             if (e.message?.contains("bundleProxyForCurrentProcess") != true) {
                throw e
            }
        }
    }
}
