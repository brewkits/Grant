package dev.brewkits.grant

import android.content.Context
import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * Android implementation of platform delegate factory.
 *
 * Requires Android Context and GrantStore.
 */
actual fun createPlatformDelegate(context: Any?, store: GrantStore): PlatformGrantDelegate {
    require(context is Context) {
        "Android requires Context. Pass applicationContext to GrantFactory.create().\n" +
        "Example: GrantFactory.create(applicationContext)"
    }
    // Always use applicationContext to prevent memory leaks
    // Even if user passes Activity context, we convert it to Application context
    return PlatformGrantDelegate(context.applicationContext, store)
}

/**
 * Android implementation of the registry-aware factory.
 *
 * The registry is **ignored** on Android — the platform's runtime permission
 * model dispatches directly through Activity Result API + `checkSelfPermission`,
 * so there are no handler classes to opt into. The opt-in DSL exists primarily
 * to enable Kotlin/Native DCE on iOS; Android consumers are free to use the
 * block form, but it has no link-time effect on the resulting APK.
 */
actual fun createPlatformDelegateWithRegistry(
    context: Any?,
    store: GrantStore,
    registrations: Map<AppGrant, () -> Any>
): PlatformGrantDelegate = createPlatformDelegate(context, store)
