package dev.brewkits.grant

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Basic iOS test to verify iOS test infrastructure works.
 */
class IosBasicTest {

    @Test
    fun basicIosTestWorks() {
        // Simple test to verify iOS testing infrastructure
        val result = 1 + 1
        assertEquals(2, result)
    }

    @Test
    fun iosTestCanAccessKotlinStdlib() {
        // Verify we can use Kotlin stdlib in iOS tests
        val list = listOf(1, 2, 3)
        assertEquals(3, list.size)
    }

    @Test
    fun iosTestCanUseStrings() {
        // String manipulation works in iOS tests
        val str = "Hello, iOS"
        assertEquals(10, str.length)
    }
}
