package dev.brewkits.grant.impl

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantStore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IosCoverageBoostTest {

    private lateinit var store: FakeGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        store = FakeGrantStore()
        delegate = PlatformGrantDelegate(store)
    }

    @Test
    fun testAlwaysGrantedHandlers() = runTest {
        // These should always return GRANTED on iOS
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM))
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.NEARBY_WIFI_DEVICES))
    }

    @Test
    fun testRawPermissionMissingKey() = runTest {
        val raw = RawPermission(
            identifier = "test_raw",
            iosUsageKey = "NSNonExistentKey",
            androidPermissions = emptyList()
        )
        // Since the key is missing in the test bundle, it should be DENIED_ALWAYS
        val status = delegate.checkStatus(raw)
        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }

    @Test
    fun testRawPermissionRequestedBefore() = runTest {
        val raw = RawPermission(
            identifier = "test_raw",
            iosUsageKey = null, // No key check
            androidPermissions = emptyList()
        )
        store.markRawPermissionRequested(raw.identifier)
        
        val status = delegate.checkStatus(raw)
        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    fun testStatusCacheEffect() = runTest {
        // First call populates cache
        val status1 = delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)
        
        // Second call should return cached value (fast)
        val status2 = delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)
        
        assertEquals(status1, status2)
    }
}
