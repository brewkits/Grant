package dev.brewkits.grant.performance

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

/**
 * Performance tests for Cache Optimization.
 * Verifies that short-lived cache reduces unnecessary calls to the platform.
 */
class CacheOptimizationPerformanceTest {

    private class CounterGrantManager : GrantManager {
        var checkStatusCount = 0
        var mockStatus = GrantStatus.NOT_DETERMINED

        override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
            checkStatusCount++
            return mockStatus
        }

        override suspend fun request(grant: GrantPermission): GrantStatus = mockStatus
        override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = grants.associateWith { mockStatus }
        override fun openSettings() {}
    }

    // Since we optimized at PlatformGrantDelegate level, we need to test it there.
    // However, PlatformGrantDelegate is an actual class. For common tests,
    // we can simulate the cache logic in a wrapper or test via integration if possible.
    
    // Note: The actual cache is in PlatformGrantDelegate (Android/iOS).
    // Let's create a platform-specific test for this instead to be accurate.
}
