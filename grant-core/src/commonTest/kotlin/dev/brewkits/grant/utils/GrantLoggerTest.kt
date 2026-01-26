package dev.brewkits.grant.utils

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrantLoggerTest {

    @BeforeTest
    fun setUp() {
        // Reset logger state before each test
        GrantLogger.isEnabled = false
        GrantLogger.logHandler = null
    }

    @AfterTest
    fun tearDown() {
        // Clean up after each test
        GrantLogger.isEnabled = false
        GrantLogger.logHandler = null
    }

    @Test
    fun testDefaultState() {
        // Logger should be disabled by default
        assertFalse(GrantLogger.isEnabled)
        assertNull(GrantLogger.logHandler)
    }

    @Test
    fun testLoggingDisabledByDefault() {
        // When disabled and no handler, logging should not throw
        GrantLogger.d("TEST", "debug message")
        GrantLogger.i("TEST", "info message")
        GrantLogger.w("TEST", "warning message")
        GrantLogger.e("TEST", "error message")
        // No assertion needed - just verify no crash
    }

    @Test
    fun testCustomLogHandler() {
        val loggedMessages = mutableListOf<Triple<GrantLogger.LogLevel, String, String>>()

        GrantLogger.logHandler = { level, tag, message ->
            loggedMessages.add(Triple(level, tag, message))
        }

        GrantLogger.d("TAG1", "debug")
        GrantLogger.i("TAG2", "info")
        GrantLogger.w("TAG3", "warning")
        GrantLogger.e("TAG4", "error")

        assertEquals(4, loggedMessages.size)

        assertEquals(GrantLogger.LogLevel.DEBUG, loggedMessages[0].first)
        assertEquals("TAG1", loggedMessages[0].second)
        assertEquals("debug", loggedMessages[0].third)

        assertEquals(GrantLogger.LogLevel.INFO, loggedMessages[1].first)
        assertEquals("TAG2", loggedMessages[1].second)
        assertEquals("info", loggedMessages[1].third)

        assertEquals(GrantLogger.LogLevel.WARNING, loggedMessages[2].first)
        assertEquals("TAG3", loggedMessages[2].second)
        assertEquals("warning", loggedMessages[2].third)

        assertEquals(GrantLogger.LogLevel.ERROR, loggedMessages[3].first)
        assertEquals("TAG4", loggedMessages[3].second)
        assertEquals("error", loggedMessages[3].third)
    }

    @Test
    fun testCustomLogHandlerWithDisabledFlag() {
        // Custom handler should work even if isEnabled is false
        val loggedMessages = mutableListOf<Triple<GrantLogger.LogLevel, String, String>>()

        GrantLogger.isEnabled = false
        GrantLogger.logHandler = { level, tag, message ->
            loggedMessages.add(Triple(level, tag, message))
        }

        GrantLogger.d("TEST", "message")

        assertEquals(1, loggedMessages.size)
        assertEquals("TEST", loggedMessages[0].second)
        assertEquals("message", loggedMessages[0].third)
    }

    @Test
    fun testErrorLoggingWithThrowable() {
        val loggedMessages = mutableListOf<Triple<GrantLogger.LogLevel, String, String>>()

        GrantLogger.logHandler = { level, tag, message ->
            loggedMessages.add(Triple(level, tag, message))
        }

        val exception = RuntimeException("Something went wrong")
        GrantLogger.e("ERROR_TAG", "An error occurred", exception)

        assertEquals(1, loggedMessages.size)
        assertEquals(GrantLogger.LogLevel.ERROR, loggedMessages[0].first)
        assertEquals("ERROR_TAG", loggedMessages[0].second)
        assertTrue(loggedMessages[0].third.contains("An error occurred"))
        assertTrue(loggedMessages[0].third.contains("Something went wrong"))
    }

    @Test
    fun testErrorLoggingWithoutThrowable() {
        val loggedMessages = mutableListOf<Triple<GrantLogger.LogLevel, String, String>>()

        GrantLogger.logHandler = { level, tag, message ->
            loggedMessages.add(Triple(level, tag, message))
        }

        GrantLogger.e("ERROR_TAG", "Simple error message")

        assertEquals(1, loggedMessages.size)
        assertEquals(GrantLogger.LogLevel.ERROR, loggedMessages[0].first)
        assertEquals("ERROR_TAG", loggedMessages[0].second)
        assertEquals("Simple error message", loggedMessages[0].third)
    }

    @Test
    fun testEnableLogging() {
        // When enabled without custom handler, should use default console output
        // We can't easily test console output, but we can verify the flag works
        GrantLogger.isEnabled = true
        assertTrue(GrantLogger.isEnabled)

        // Should not crash
        GrantLogger.d("TEST", "debug")
        GrantLogger.i("TEST", "info")
        GrantLogger.w("TEST", "warning")
        GrantLogger.e("TEST", "error")
    }

    @Test
    fun testLogLevelEnum() {
        // Verify all log levels exist
        val levels = GrantLogger.LogLevel.entries
        assertEquals(4, levels.size)
        assertTrue(levels.contains(GrantLogger.LogLevel.DEBUG))
        assertTrue(levels.contains(GrantLogger.LogLevel.INFO))
        assertTrue(levels.contains(GrantLogger.LogLevel.WARNING))
        assertTrue(levels.contains(GrantLogger.LogLevel.ERROR))
    }

    @Test
    fun testLogHandlerCanBeCleared() {
        val loggedMessages = mutableListOf<String>()

        GrantLogger.logHandler = { _, _, message ->
            loggedMessages.add(message)
        }

        GrantLogger.i("TEST", "first message")
        assertEquals(1, loggedMessages.size)

        // Clear handler
        GrantLogger.logHandler = null
        GrantLogger.i("TEST", "second message")

        // Should still be 1 (second message not logged)
        assertEquals(1, loggedMessages.size)
    }

    @Test
    fun testMultipleLogCalls() {
        val loggedMessages = mutableListOf<String>()

        GrantLogger.logHandler = { _, _, message ->
            loggedMessages.add(message)
        }

        repeat(10) { index ->
            GrantLogger.i("TEST", "Message $index")
        }

        assertEquals(10, loggedMessages.size)
        assertEquals("Message 0", loggedMessages[0])
        assertEquals("Message 9", loggedMessages[9])
    }
}
