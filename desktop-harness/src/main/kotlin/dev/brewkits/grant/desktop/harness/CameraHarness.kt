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

/**
 * Tier 2's (ROADMAP.md v2.6.0) manual verification harness — see this module's
 * `build.gradle.kts` header for why it's a standalone, unpublished module rather than living
 * inside `demo`.
 *
 * The only run target that proves anything: `./gradlew :desktop-harness:runDistributable`
 * launches this from the actual packaged `.app` bundle (with `NSCameraUsageDescription` set),
 * which is what gives macOS TCC a real identity to attribute the camera prompt to.
 * `:desktop-harness:run` (unbundled) is worth one run too, but only as a *negative* control —
 * if the dialog appears there identically, this harness isn't isolating what it claims to.
 */
fun main() {
    GrantDesktop.initialize()
    val grantManager = GrantFactory.create()

    application {
        Window(onCloseRequest = ::exitApplication, title = "Grant — macOS Tier 2 Camera Harness") {
            Surface(modifier = Modifier.fillMaxSize()) {
                var statusText by remember { mutableStateOf("Not checked yet") }
                val scope = rememberCoroutineScope()

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Camera status: $statusText", style = MaterialTheme.typography.bodyLarge)

                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = {
                            scope.launch {
                                statusText = grantManager.checkStatus(AppGrant.CAMERA).name
                            }
                        }) {
                            Text("Check Status")
                        }

                        Button(onClick = {
                            scope.launch {
                                statusText = grantManager.request(AppGrant.CAMERA).name
                            }
                        }) {
                            Text("Request Camera")
                        }

                        Button(onClick = { grantManager.openSettings() }) {
                            Text("Open Settings")
                        }
                    }
                }
            }
        }
    }
}
