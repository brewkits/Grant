package dev.brewkits.grant.di

import dev.brewkits.grant.GrantAndServiceChecker
import dev.brewkits.grant.GrantManager
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Contract tests for the Koin wiring.
 *
 * **What the previous version of this test did not do.** It called
 * `startKoin { modules(grantModule) }` and asserted nothing at all. That passes whatever the
 * module contains, because Koin resolves lazily: registering a definition whose dependencies
 * are missing is not an error until something asks for it. A broken graph would have shipped
 * green and failed at the consumer's first `get()`.
 *
 * **Why not `Module.verify()` or inspecting `Module.mappings`:** the static graph verifier is
 * JVM-only (reflection-based), so it cannot run in a `commonTest` that also targets
 * Kotlin/Native — and `mappings` is `@KoinInternalAPI`, which would tie this library's tests to
 * Koin's internals and break on a routine upgrade. These tests therefore assert *behaviour*
 * through the public API only.
 */
class GrantDiTest : KoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    /**
     * The documented contract: `grantModule` alone is not enough.
     *
     * `GrantManager` depends on `PlatformGrantDelegate`, which only `grantPlatformModule`
     * provides. Loading just `grantModule` must therefore fail at resolution — and fail
     * *loudly*, rather than hand back a half-built object. README and the module KDoc both tell
     * consumers to load both modules; this pins that instruction to observable behaviour, so a
     * refactor that quietly made `grantModule` self-satisfying (by constructing a delegate
     * itself, say) fails here instead of changing what every app depends on.
     */
    @Test
    fun grantModule_alone_cannot_resolve_GrantManager_without_the_platform_module() {
        startKoin { modules(grantModule) }

        assertFailsWith<Exception>(
            "resolving GrantManager without grantPlatformModule must fail — the missing " +
                "PlatformGrantDelegate binding is what makes loading both modules mandatory",
        ) {
            getKoin().get<GrantManager>()
        }
    }

    /**
     * The same contract for the convenience type, which is what most consumers actually inject.
     *
     * Worth its own case rather than trusting the one above: `GrantAndServiceChecker` depends on
     * `GrantManager` *and* `ServiceManager`, so it fails for a different reason and through a
     * different resolution path. A change that satisfied only one of those would leave this one
     * broken while the first test still passed.
     */
    @Test
    fun grantModule_alone_cannot_resolve_GrantAndServiceChecker() {
        startKoin { modules(grantModule) }

        assertFailsWith<Exception> {
            getKoin().get<GrantAndServiceChecker>()
        }
    }

    /**
     * After `stopKoin()`, resolution must fail rather than return a stale instance from the
     * previous container.
     *
     * This is the state every test in this class leaves behind via `tearDown`, and a leak here
     * would make later tests pass for the wrong reason — a failure mode that hides itself.
     */
    @Test
    fun resolution_fails_once_koin_is_stopped() {
        startKoin { modules(grantModule) }
        stopKoin()

        assertFailsWith<Exception> {
            getKoin().get<GrantManager>()
        }
    }
}
