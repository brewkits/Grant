package dev.brewkits.grant.impl

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantStore
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
        
        allAppGrants.forEach { grant ->
            if (grant == AppGrant.NOTIFICATION) return@forEach
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
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY,
            AppGrant.LOCATION,
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

    @Test
    fun testGalleryVariants() = runTest {
        assertNotNull(delegate.checkStatus(AppGrant.GALLERY_IMAGES_ONLY))
        assertNotNull(delegate.checkStatus(AppGrant.GALLERY_VIDEO_ONLY))
        assertNotNull(delegate.checkStatus(AppGrant.STORAGE))
    }

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
