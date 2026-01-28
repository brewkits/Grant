package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for SavedStateDelegate implementations.
 */
class SavedStateDelegateTest {

    @Test
    fun `NoOpSavedStateDelegate - saveState does nothing`() {
        val delegate = NoOpSavedStateDelegate()

        // Should not throw
        delegate.saveState("key", "value")
    }

    @Test
    fun `NoOpSavedStateDelegate - restoreState always returns null`() {
        val delegate = NoOpSavedStateDelegate()

        delegate.saveState("key", "value")
        val restored = delegate.restoreState("key")

        assertNull(restored, "NoOpSavedStateDelegate should always return null")
    }

    @Test
    fun `NoOpSavedStateDelegate - clear does nothing`() {
        val delegate = NoOpSavedStateDelegate()

        // Should not throw
        delegate.clear("key")
    }

    @Test
    fun `SavedStateDelegate - interface contract`() {
        // Verify interface can be implemented
        val customDelegate = object : SavedStateDelegate {
            private val storage = mutableMapOf<String, String>()

            override fun saveState(key: String, value: String) {
                storage[key] = value
            }

            override fun restoreState(key: String): String? {
                return storage[key]
            }

            override fun clear(key: String) {
                storage.remove(key)
            }
        }

        // Test custom implementation
        customDelegate.saveState("test", "value")
        assertEquals("value", customDelegate.restoreState("test"))

        customDelegate.clear("test")
        assertNull(customDelegate.restoreState("test"))
    }
}
