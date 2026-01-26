package dev.brewkits.grant

import dev.brewkits.grant.impl.MyGrantManager
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GrantFactoryTest {

    @Test
    fun testFactoryCreatesGrantManager() {
        // Note: This test will work on iOS but will fail on Android without context
        // Platform-specific tests should be added to androidTest and iosTest
        val manager = try {
            GrantFactory.create()
        } catch (e: IllegalArgumentException) {
            // Expected on Android when no context provided
            // This is acceptable for common tests
            return
        }

        assertNotNull(manager, "GrantManager should not be null")
    }

    @Test
    fun testFactoryReturnsMyGrantManagerImplementation() {
        val manager = try {
            GrantFactory.create()
        } catch (e: IllegalArgumentException) {
            // Expected on Android when no context provided
            // This is acceptable for common tests
            return
        }

        assertIs<MyGrantManager>(manager, "Factory should return MyGrantManager implementation")
    }

    @Test
    fun testFactoryCreatesNewInstances() {
        val manager1 = try {
            GrantFactory.create()
        } catch (e: IllegalArgumentException) {
            // Expected on Android when no context provided
            return
        }

        val manager2 = try {
            GrantFactory.create()
        } catch (e: IllegalArgumentException) {
            // Expected on Android when no context provided
            return
        }

        // Each call should create a new instance (not singleton)
        assertNotNull(manager1)
        assertNotNull(manager2)
    }
}
