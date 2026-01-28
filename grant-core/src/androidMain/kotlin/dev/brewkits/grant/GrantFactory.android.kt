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
