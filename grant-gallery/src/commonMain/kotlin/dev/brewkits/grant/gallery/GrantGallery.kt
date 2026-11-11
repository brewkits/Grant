package dev.brewkits.grant.gallery

/**
 * Entry point of the `grant-gallery` module, which provides the photo library handlers (`AppGrant.GALLERY`, `GALLERY_IMAGES_ONLY`, `GALLERY_VIDEO_ONLY`, `GALLERY_ADD_ONLY`, `STORAGE`).
 *
 * On iOS these live outside `grant-core` so that an app which never uses them does not link
 * `Photos.framework`, and App Store Connect does not ask it for NSPhotoLibraryUsageDescription / NSPhotoLibraryAddUsageDescription (Issue #91).
 *
 * Call [initialize] once at startup, before the first request, on every platform. It is safe
 * to call from common code and to call more than once.
 */
public expect object GrantGallery {
    /**
     * Registers this module's handlers with `grant-core`. Required on iOS. A no-op on the other
     * platforms, where `grant-core` handles these permissions itself.
     */
    public fun initialize()
}
