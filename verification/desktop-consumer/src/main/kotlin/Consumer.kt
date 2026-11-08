import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantFactory
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.desktop.GrantDesktop
import dev.brewkits.grant.desktop.WindowsPrivacyStatus
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) {
    if ("--demo" in args) {
        desktopDemo()
        return
    }
    val output = args.firstOrNull { it.startsWith("--out=") }?.removePrefix("--out=")
    try {
        val windows = System.getProperty("os.name").startsWith("Windows")
        val nativeResource = if (windows) "/win32-x86-64/GrantWindowsBridge.dll"
            else "/darwin-aarch64/libGrantDesktopBridge.dylib"
        check(GrantDesktop::class.java.getResource(nativeResource) != null)
        GrantDesktop.initialize()
        GrantDesktop.initialize()
        if (!windows) {
            check(GrantDesktop.isSupported(AppGrant.CAMERA))
            check(GrantDesktop.isSupported(AppGrant.MICROPHONE))
        }
        check(!GrantDesktop.isSupported(AppGrant.CONTACTS))
        val manager = GrantFactory.create()
        val report = runBlocking {
            buildString {
                if (windows) {
                    for (grant in listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)) {
                        val status = GrantDesktop.checkWindowsPrivacyStatus(grant)
                        check(status != WindowsPrivacyStatus.UNAVAILABLE) { "$grant privacy API unavailable" }
                        appendLine("WINDOWS_${grant.name}=$status")
                    }
                    check(!GrantDesktop.isSupported(AppGrant.CAMERA))
                    appendLine("NATIVE_PERMISSION_REQUESTS=UNSUPPORTED")
                }
                appendLine("BRIDGE=LOADED")
                appendLine("CAMERA=${manager.checkStatus(AppGrant.CAMERA)}")
                appendLine("MICROPHONE=${manager.checkStatus(AppGrant.MICROPHONE)}")
                check(manager.checkStatus(AppGrant.CONTACTS) == GrantStatus.DENIED_ALWAYS)
                appendLine("UNSUPPORTED_CONTACTS=DENIED_ALWAYS")
                // Explicit opt-in only: ordinary smoke verification never prompts for consent.
                if ("--request-camera" in args && GrantDesktop.isSupported(AppGrant.CAMERA)) {
                    appendLine("CAMERA_REQUEST=${manager.request(AppGrant.CAMERA)}")
                }
                if ("--request-mic" in args && GrantDesktop.isSupported(AppGrant.MICROPHONE)) {
                    appendLine("MICROPHONE_REQUEST=${manager.request(AppGrant.MICROPHONE)}")
                }
                appendLine("RESULT=PASS")
            }
        }
        output?.let { File(it).writeText(report) }
        print(report)
    } catch (failure: Throwable) {
        output?.let { File(it).writeText("RESULT=FAIL\n${failure.stackTraceToString()}") }
        throw failure
    }
}
