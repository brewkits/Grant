package dev.brewkits.grant

import android.content.Context
import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * Android implementation of platform delegate factory.
 *
 * Requires an Android [Context]. When [store] is `null`, a persistent
 * [SharedPreferencesGrantStore] is used so request history survives process death.
 */
actual fun createPlatformDelegate(context: Any?, store: GrantStore?): PlatformGrantDelegate {
    require(context is Context) {
        "Android requires Context. Pass applicationContext to GrantFactory.create().\n" +
        "Example: GrantFactory.create(applicationContext)"
    }
    // Always use applicationContext to prevent memory leaks
    // Even if user passes Activity context, we convert it to Application context
    val appContext = context.applicationContext
    return PlatformGrantDelegate(
        appContext,
        store ?: SharedPreferencesGrantStore(appContext)
    )
}
