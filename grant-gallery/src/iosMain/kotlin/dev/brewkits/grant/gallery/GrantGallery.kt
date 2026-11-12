package dev.brewkits.grant.gallery

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import dev.brewkits.grant.handlers.PhotoPermissionHandler
import platform.Photos.PHAccessLevelAddOnly

/** iOS: registers the Photos-backed handlers for the `GALLERY*` grants and [AppGrant.STORAGE]. */
public actual object GrantGallery {
    private var isInitialized = false

    public actual fun initialize() {
        if (isInitialized) return
        isInitialized = true
        // iOS has one read/write photo-library authorization. The images/video split is an
        // Android distinction, so all four share one handler, as they did inside grant-core.
        val readWrite = PhotoPermissionHandler()
        listOf(AppGrant.GALLERY, AppGrant.STORAGE, AppGrant.GALLERY_IMAGES_ONLY, AppGrant.GALLERY_VIDEO_ONLY)
            .forEach { IosPermissionHandlerRegistry.register(it.identifier, readWrite) }
        // Save-only access is a separate, smaller authorization with its own Info.plist key.
        IosPermissionHandlerRegistry.register(
            AppGrant.GALLERY_ADD_ONLY.identifier,
            PhotoPermissionHandler(
                accessLevel = PHAccessLevelAddOnly,
                usageDescriptionKey = "NSPhotoLibraryAddUsageDescription",
            ),
        )
    }
}
