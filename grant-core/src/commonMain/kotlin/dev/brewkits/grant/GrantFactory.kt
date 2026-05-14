package dev.brewkits.grant

import dev.brewkits.grant.impl.DefaultGrantManager
import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * A static factory for creating [GrantManager] instances without a Dependency
 * Injection framework.
 *
 * Use this for "Manual Injection" if your project does not use Koin or other DI tools.
 *
 * ### Recommended usage — opt-in DSL
 * ```kotlin
 * val grantManager = GrantFactory.create(applicationContext) {
 *     location()
 *     locationAlways()
 *     bluetooth()
 *     notification()
 * }
 * ```
 *
 * Calling only the permissions an app actually uses lets Kotlin/Native DCE strip the
 * other handlers and their framework imports, which prevents Apple's static analyzer
 * from demanding unused `NSUsageDescription` keys. See
 * [issue #38](https://github.com/brewkits/Grant/issues/38) for background.
 */
object GrantFactory {
    /**
     * Creates a [GrantManager] that supports only the permissions registered inside [block].
     *
     * Any permission requested at runtime that was not registered will return an
     * error / unsupported status — call sites should declare every permission they need.
     *
     * @param context Platform-specific context (required on Android, ignored on iOS).
     * @param store Storage implementation for tracking request history.
     * @param block DSL block that calls the per-permission extensions on [GrantBuilder].
     */
    fun create(
        context: Any? = null,
        store: GrantStore = InMemoryGrantStore(),
        block: GrantBuilder.() -> Unit
    ): GrantManager {
        val builder = GrantBuilder().apply(block)
        val delegate = createPlatformDelegateWithRegistry(context, store, builder.registrations)
        return DefaultGrantManager(delegate)
    }

    /**
     * Legacy no-arg factory.
     *
     * Registers every built-in handler internally, which preserves the v1.4.x linking
     * behavior — every framework is linked, every `NSUsageDescription` key is still
     * required. New code should switch to the block form so K/N DCE can strip
     * handlers an app never uses.
     */
    @Deprecated(
        message = "Use the block form for selective handler registration. The no-arg form " +
            "registers every handler, which forces App Store to require NSUsageDescription " +
            "keys for unused frameworks (CoreMotion, EventKit, Contacts). " +
            "See https://github.com/brewkits/Grant/issues/38.",
        replaceWith = ReplaceWith("create(context, store) { registerAll() }")
    )
    fun create(
        context: Any? = null,
        store: GrantStore = InMemoryGrantStore()
    ): GrantManager = create(context, store) { registerAll() }
}

/**
 * Platform-specific factory function. Internal use only.
 *
 * @deprecated Kept for the legacy no-arg path on platforms that haven't migrated to
 *   the registry-based delegate yet. New code goes through
 *   [createPlatformDelegateWithRegistry].
 */
expect fun createPlatformDelegate(context: Any?, store: GrantStore): PlatformGrantDelegate

/**
 * Platform-specific factory that wires a registry of handler factories into the
 * delegate. iOS uses the registry as its single source of truth for permission
 * dispatch; Android ignores the registry (its delegate maps directly to native APIs).
 */
expect fun createPlatformDelegateWithRegistry(
    context: Any?,
    store: GrantStore,
    registrations: Map<AppGrant, () -> Any>
): PlatformGrantDelegate
