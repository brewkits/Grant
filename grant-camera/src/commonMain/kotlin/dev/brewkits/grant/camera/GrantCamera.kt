package dev.brewkits.grant.camera

/**
 * Entry point of the `grant-camera` module, which provides the camera and microphone handlers (`AppGrant.CAMERA`, `AppGrant.MICROPHONE`).
 *
 * On iOS these live outside `grant-core` so that an app which never uses them does not link
 * `AVFoundation.framework`, and App Store Connect does not ask it for NSCameraUsageDescription / NSMicrophoneUsageDescription (Issue #91).
 *
 * Call [initialize] once at startup, before the first request, on every platform. It is safe
 * to call from common code and to call more than once.
 */
public expect object GrantCamera {
    /**
     * Registers this module's handlers with `grant-core`. Required on iOS. A no-op on the other
     * platforms, where `grant-core` handles these permissions itself.
     */
    public fun initialize()
}
