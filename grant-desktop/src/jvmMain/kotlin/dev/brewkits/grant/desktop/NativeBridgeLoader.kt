package dev.brewkits.grant.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import dev.brewkits.grant.utils.GrantLogger
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

private const val TAG = "GrantDesktopBridge"

/**
 * The C surface `CameraBridge.kt`/`SettingsBridge.kt` (Kotlin/Native, `macosMain`) export —
 * see those files.
 */
internal interface GrantDesktopBridgeLibrary : Library {
    /** Raw `AVAuthorizationStatus`: 0 = NotDetermined, 1 = Restricted, 2 = Denied, 3 = Authorized. */
    fun grant_camera_status(): Int

    /**
     * Blocks the calling (JVM) thread until the macOS consent dialog is dismissed — call this
     * from a background thread, never from a UI thread. Returns the same raw status as
     * [grant_camera_status], re-read after the callback, not the completion handler's boolean.
     */
    fun grant_camera_request_blocking(): Int

    /** Same contract as [grant_camera_status], for the microphone. */
    fun grant_microphone_status(): Int

    /** Same contract as [grant_camera_request_blocking], for the microphone. */
    fun grant_microphone_request_blocking(): Int

    /**
     * Opens System Settings' Privacy & Security pane at [anchor] (e.g. `"Privacy_Camera"`,
     * `"Privacy_Microphone"`) via `NSWorkspace`. Returns whether the URL was handled.
     */
    fun grant_open_privacy_settings(anchor: String): Boolean
}

/**
 * Loads `libGrantDesktopBridge.dylib` from this module's own classpath resources (bundled by
 * `build.gradle.kts`'s `copyMacosArm64Dylib` task) and exposes the typed JNA interface for it.
 *
 * JNA cannot load a native library directly out of a jar entry — it needs a real file — so this
 * extracts the resource to a temp file first, mirroring JNA's own internal
 * `Native.extractFromResourcePath` mechanism (not reused directly because that method resolves
 * against `jna.library.path`/the default JNA resource layout, and this bridge intentionally
 * uses its own `darwin-<arch>` resource root instead of colliding with JNA's).
 */
internal object NativeBridgeLoader {
    /** `null` when the current OS/arch has no bundled dylib — e.g. every non-macOS JVM. */
    val library: GrantDesktopBridgeLibrary? by lazy { load() }

    private fun load(): GrantDesktopBridgeLibrary? {
        val resourcePath = resourcePathForCurrentPlatform()
        if (resourcePath == null) {
            GrantLogger.d(TAG, "No bundled dylib for this OS/arch (${System.getProperty("os.name")}/${System.getProperty("os.arch")}); Tier 2 macOS support is inactive here.")
            return null
        }

        val resourceStream = NativeBridgeLoader::class.java.getResourceAsStream(resourcePath)
        if (resourceStream == null) {
            GrantLogger.w(TAG, "Bundled dylib resource '$resourcePath' not found on the classpath. " +
                "This build of grant-desktop may be missing its native artifact.")
            return null
        }

        return try {
            // File.createTempFile() defaults to world-readable permissions on POSIX systems
            // (CodeQL java/local-temp-file-or-directory-information-disclosure) — this is
            // macOS-only code (see resourcePathForCurrentPlatform), so an explicit
            // owner-only-rwx POSIX attribute is always safe to request here, unlike code that
            // also has to run on Windows.
            val ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
            val tempPath = Files.createTempFile("libGrantDesktopBridge", ".dylib", ownerOnly)
            val tempFile = tempPath.toFile()
            tempFile.deleteOnExit()
            resourceStream.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
            Native.load(tempFile.absolutePath, GrantDesktopBridgeLibrary::class.java)
        } catch (e: Throwable) {
            GrantLogger.e(TAG, "Failed to load the native macOS bridge", e)
            null
        }
    }

    private fun resourcePathForCurrentPlatform(): String? {
        val osName = System.getProperty("os.name")?.lowercase() ?: return null
        if (!osName.contains("mac")) return null

        val arch = System.getProperty("os.arch")?.lowercase() ?: return null
        val archDir = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "darwin-aarch64"
            // x86_64 slice not yet built — see ROADMAP.md v2.6.0, "Cost: two arch slices".
            else -> return null
        }
        return "/$archDir/libGrantDesktopBridge.dylib"
    }
}
