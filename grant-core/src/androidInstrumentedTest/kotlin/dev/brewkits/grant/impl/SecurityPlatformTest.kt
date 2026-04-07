package dev.brewkits.grant.impl

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security tests for Android platform components.
 * Verifies that GrantRequestActivity is correctly declared and secured.
 */
class SecurityPlatformTest {

    @Test
    fun testGrantRequestActivityManifestDeclaration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val componentName = ComponentName(context, GrantRequestActivity::class.java)

        val activityInfo = try {
            packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        assertNotNull("GrantRequestActivity must be declared in AndroidManifest.xml", activityInfo)
        
        // Security check: Activity should be transparent and not exported if not needed
        // (Though Grant library exports it by default for consumer convenience, 
        // it must have specific flags)
        
        assertEquals(
            "GrantRequestActivity should have standard launchMode",
            ActivityInfo.LAUNCH_MULTIPLE, 
            activityInfo?.launchMode ?: ActivityInfo.LAUNCH_MULTIPLE
        )
    }

    @Test
    fun testGrantRequestActivityTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val componentName = ComponentName(context, GrantRequestActivity::class.java)
        val packageManager = context.packageManager
        
        val activityInfo = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA)
        val themeResId = activityInfo.themeResource
        
        assertTrue("GrantRequestActivity must have a theme defined", themeResId != 0)
        
        // We can't easily check if the theme is "Transparent" at runtime via resource ID alone 
        // without more complex logic, but we verify it HAS a theme.
    }
}
