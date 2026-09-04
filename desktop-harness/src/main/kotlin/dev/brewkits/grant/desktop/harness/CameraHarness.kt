package dev.brewkits.grant.desktop.harness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantFactory
import dev.brewkits.grant.desktop.GrantDesktop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Tier 2's (ROADMAP.md v2.6.0) manual verification harness — see this module's
 * `build.gradle.kts` header for why it's a standalone, unpublished module rather than living
 * inside `demo`.
 *
 * **Launch it with `open -a`, never by running the executable directly.** This is not a style
 * preference — it is the difference between measuring this app and measuring your terminal.
 * Verified, not assumed: after `tccutil reset Camera dev.brewkits.grant.desktopharness` and
 * `tccutil reset Microphone …` (i.e. this bundle has no TCC record at all), the two launch
 * modes disagree:
 *
 * ```
 * $ GrantDesktopHarness.app/Contents/MacOS/GrantDesktopHarness --headless
 * CAMERA=GRANTED        MICROPHONE=GRANTED        <- the launching terminal's record
 *
 * $ open -a GrantDesktopHarness.app --args --headless --out=/tmp/r.txt
 * CAMERA=NOT_DETERMINED MICROPHONE=NOT_DETERMINED <- this bundle's own record
 * ```
 *
 * Running the executable directly makes macOS attribute permissions to the *responsible*
 * process — the terminal or IDE that spawned it — so a bridge that reads nothing at all can
 * look like it works, inheriting whatever the IDE was already granted. That is the exact class
 * of false-positive this whole tier exists to avoid, and it is invisible unless you compare the
 * two modes. `./gradlew :desktop-harness:run` has the same problem for the same reason; use
 * `runDistributable`, or `open -a` on the built `.app`.
 *
 * `--headless` (optionally with `--out=<path>`, since `open` discards stdout) reads statuses
 * without a dialog, which makes the mapping scriptable: pair it with `tccutil reset` to drive a
 * real NOT_DETERMINED transition. `--open-settings` exercises the `NSWorkspace` deep link.
 * Requesting still needs a human — macOS deliberately does not let a process synthesise clicks
 * onto a TCC dialog.
 */
fun main(args: Array<String>) {
    GrantDesktop.initialize()
    val grantManager = GrantFactory.create()

    // Headless status read, for scripted verification of the status *mapping* (the half that
    // needs no human to click a dialog). Runs inside the same packaged .app, so it carries the
    // same code-signing identity TCC attributes permissions to — the whole reason this harness
    // is a bundle and not a `jvmTest`. Paired with `tccutil reset Camera <bundleID>`, this
    // drives a real, verifiable NOT_DETERMINED ⇄ GRANTED transition end to end without
    // simulating anything: the OS's own database is what changes, and Grant reports what it
    // reads back. `--request-camera`/`--request-mic` still need a human for the dialog.
    if (args.contains("--headless")) {
        val report = runBlocking {
            buildString {
                appendLine("CAMERA=${grantManager.checkStatus(AppGrant.CAMERA).name}")
                appendLine("MICROPHONE=${grantManager.checkStatus(AppGrant.MICROPHONE).name}")
                // A permission with no desktop handler registered must report DENIED_ALWAYS,
                // never a fabricated GRANTED — the Calf-style regression this tier exists to
                // avoid.
                appendLine("CONTACTS=${grantManager.checkStatus(AppGrant.CONTACTS).name}")
            }
        }
        print(report)
        // `open -a Foo.app --args ...` is the only launch that gives TCC this bundle's own
        // identity rather than the launching terminal's, and it discards stdout — so the same
        // report is written to a file when `--out=<path>` is passed, making that launch mode
        // observable at all.
        args.firstOrNull { it.startsWith("--out=") }
            ?.removePrefix("--out=")
            ?.let { File(it).writeText(report) }
        exitProcess(0)
    }

    if (args.contains("--open-settings")) {
        grantManager.openSettings()
        // Give NSWorkspace time to hand the URL to System Settings before the process exits.
        Thread.sleep(2000)
        exitProcess(0)
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "Grant — macOS Tier 2 Camera Harness") {
            Surface(modifier = Modifier.fillMaxSize()) {
                var cameraStatus by remember { mutableStateOf("Not checked yet") }
                var micStatus by remember { mutableStateOf("Not checked yet") }
                val scope = rememberCoroutineScope()

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Camera status: $cameraStatus", style = MaterialTheme.typography.bodyLarge)

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = {
                            scope.launch {
                                cameraStatus = grantManager.checkStatus(AppGrant.CAMERA).name
                            }
                        }) {
                            Text("Check Camera")
                        }

                        Button(onClick = {
                            scope.launch {
                                cameraStatus = grantManager.request(AppGrant.CAMERA).name
                            }
                        }) {
                            Text("Request Camera")
                        }
                    }

                    Text(
                        "Microphone status: $micStatus",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 24.dp),
                    )

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = {
                            scope.launch {
                                micStatus = grantManager.checkStatus(AppGrant.MICROPHONE).name
                            }
                        }) {
                            Text("Check Mic")
                        }

                        Button(onClick = {
                            scope.launch {
                                micStatus = grantManager.request(AppGrant.MICROPHONE).name
                            }
                        }) {
                            Text("Request Mic")
                        }
                    }

                    Button(
                        onClick = { grantManager.openSettings() },
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        Text("Open Privacy Settings")
                    }
                }
            }
        }
    }
}
