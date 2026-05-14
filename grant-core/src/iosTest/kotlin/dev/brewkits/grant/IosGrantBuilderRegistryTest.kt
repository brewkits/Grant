package dev.brewkits.grant

import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * iOS-specific tests for the registry-driven [PlatformGrantDelegate].
 *
 * These tests verify that:
 * - Permissions registered via the DSL are dispatched correctly.
 * - Permissions NOT registered raise an actionable error mentioning the
 *   missing extension function.
 * - The legacy `PlatformGrantDelegate(store)` constructor still works
 *   (it auto-registers everything via `registerAll()`).
 */
class IosGrantBuilderRegistryTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED,
        GrantStatus.PARTIAL_GRANTED,
        GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS,
        GrantStatus.NOT_DETERMINED
    )

    @Test
    fun `block form with bluetooth and location yields working manager`() {
        val manager = GrantFactory.create {
            bluetooth()
            location()
        }
        assertNotNull(manager)
    }

    @Test
    fun `registered permission dispatches without throwing`() = runTest {
        val manager = GrantFactory.create {
            scheduleExactAlarm()
        }
        // SCHEDULE_EXACT_ALARM is the always-granted handler — safe on simulator.
        val status = manager.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)
        assertEquals(GrantStatus.GRANTED, status)
    }

    @Test
    fun `unregistered permission raises actionable error`() = runTest {
        val manager = GrantFactory.create {
            // Only register bluetooth; CONTACTS is intentionally absent.
            bluetooth()
        }

        val exception = assertFails {
            manager.checkStatus(AppGrant.CONTACTS)
        }
        val message = exception.message ?: ""
        assertTrue(
            "CONTACTS" in message,
            "Error message should mention which permission was missing. Got: $message"
        )
        assertTrue(
            "contacts()" in message || "registerAll" in message,
            "Error message should suggest the fix (call contacts() or registerAll()). Got: $message"
        )
    }

    @Test
    fun `legacy no-arg constructor still works`() = runTest {
        // Existing tests/consumers that built the delegate directly continue to work.
        val delegate = PlatformGrantDelegate(InMemoryGrantStore())
        val status = delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)
        assertEquals(GrantStatus.GRANTED, status)
    }

    @Test
    fun `registerAll matches legacy behavior for simulator-safe permissions`() = runTest {
        val manager = GrantFactory.create { registerAll() }
        val simulatorSafe = listOf(
            AppGrant.SCHEDULE_EXACT_ALARM,
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.LOCATION,
            AppGrant.BLUETOOTH
        )
        simulatorSafe.forEach { grant ->
            val status = manager.checkStatus(grant)
            assertTrue(
                status in validStatuses,
                "registerAll() must dispatch ${grant.name} correctly; got $status"
            )
        }
    }
}
