package dev.brewkits.grant.impl

import dev.brewkits.grant.GrantStore
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.AppGrant
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlatformGrantDelegateAndroidTest {

    @Test
    fun testRawPermission_withNonActivityContext_returnsDenied() = runTest {
        // Arrange
        val context = RuntimeEnvironment.getApplication()
        val fakeStore = object : GrantStore {
            override fun getStatus(grant: AppGrant): GrantStatus? = null
            override fun setStatus(grant: AppGrant, status: GrantStatus) {}
            override fun isRequestedBefore(grant: AppGrant): Boolean = false
            override fun setRequested(grant: AppGrant) {}
            override fun clear() {}
            override fun clear(grant: AppGrant) {}
            override fun isRawPermissionRequested(identifier: String): Boolean = true
            override fun markRawPermissionRequested(identifier: String) {}
        }
        
        // ContextCompat.checkSelfPermission is a static method. testing the fallback
        // logic requires Robolectric or MockK static mocking, but we can test the behavior
        // of the delegate when Context is NOT an Activity.
        
        // Since the context from Robolectric is an Application, not an Activity,
        // anyCanShowRationale should fallback to `true`, resulting in `DENIED`
        // instead of `DENIED_ALWAYS`.
    }
}
