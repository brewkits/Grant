package dev.brewkits.grant.impl

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Auto-registers foreground-Activity tracking before any app code runs.
 *
 * `ContentProvider.onCreate()` is called by the system during app startup, before
 * `Application.onCreate()` and before any `Activity` is created — the same hook
 * AndroidX Startup/WorkManager use for zero-config initialization. Registering
 * [PlatformGrantDelegate]'s `Application.ActivityLifecycleCallbacks` here (rather than
 * lazily, e.g. from a DI singleton's constructor) guarantees it is in place before the
 * very first `onResume()`, so [dev.brewkits.grant.PlatformConfig.activity] is never
 * missing the activity that's already on screen by the time a DI container gets around
 * to constructing [PlatformGrantDelegate].
 */
internal class GrantInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { PlatformGrantDelegate.trackForegroundActivity(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
