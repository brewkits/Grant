package dev.brewkits.grant

import androidx.lifecycle.SavedStateHandle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidSavedStateDelegateTest {

    @Test
    fun `test save and restore state`() {
        val handle = SavedStateHandle()
        val delegate = AndroidSavedStateDelegate(handle)
        
        delegate.saveState("key", "value")
        assertEquals("value", delegate.restoreState("key"))
        assertEquals("value", handle.get<String>("key"))
        
        delegate.clear("key")
        assertNull(delegate.restoreState("key"))
    }
}
