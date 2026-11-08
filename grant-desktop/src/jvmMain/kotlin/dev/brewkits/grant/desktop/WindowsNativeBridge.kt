package dev.brewkits.grant.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import dev.brewkits.grant.utils.GrantLogger
import java.nio.file.Files

internal interface WindowsBridgeLibrary : Library {
    fun grant_windows_bridge_version(): Int
    fun grant_windows_access_status(permission: Int): Int
    fun grant_windows_open_settings(permission: Int): Int
}

internal object WindowsNativeBridge {
    val library: WindowsBridgeLibrary? by lazy { load() }

    private fun load(): WindowsBridgeLibrary? {
        if (!System.getProperty("os.name").startsWith("Windows")) return null
        if (System.getProperty("os.arch").lowercase() !in listOf("amd64", "x86_64")) return null
        val resource = "/win32-x86-64/GrantWindowsBridge.dll"
        return try {
            val stream = WindowsNativeBridge::class.java.getResourceAsStream(resource)
            if (stream == null) {
                GrantLogger.w("GrantWindowsBridge", "Missing bundled Windows x64 bridge: $resource")
                return null
            }
            stream.use { input ->
                val directory = Files.createTempDirectory("grant-windows-").toFile()
                directory.deleteOnExit()
                val dll = directory.resolve("GrantWindowsBridge.dll")
                dll.deleteOnExit()
                dll.outputStream().use { output -> input.copyTo(output) }
                Native.load(dll.absolutePath, WindowsBridgeLibrary::class.java).also {
                    check(it.grant_windows_bridge_version() == 1) { "Unsupported Windows bridge ABI" }
                }
            }
        } catch (failure: Exception) {
            GrantLogger.e("GrantWindowsBridge", "Windows privacy API is unavailable", failure)
            null
        } catch (failure: LinkageError) {
            GrantLogger.e("GrantWindowsBridge", "Windows native bridge could not be linked", failure)
            null
        }
    }
}
