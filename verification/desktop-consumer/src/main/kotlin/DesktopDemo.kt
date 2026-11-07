import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun desktopDemo() {
    GrantDesktop.initialize()
    val manager = GrantFactory.create()
    val windows = System.getProperty("os.name").startsWith("Windows")
    application {
        Window(onCloseRequest = ::exitApplication, title = "Grant Desktop preview") {
            MaterialTheme {
                Surface {
                    val scope = rememberCoroutineScope()
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(if (windows) "Windows privacy access" else "macOS camera and microphone")
                        if (windows) Text("Privacy access does not guarantee a device is present or ready to capture.")
                        for (grant in listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)) {
                            var status by remember { mutableStateOf("Not checked") }
                            var busy by remember { mutableStateOf(false) }
                            Text("${grant.name}: $status")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(enabled = !busy, onClick = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            status = withContext(Dispatchers.IO) {
                                                if (windows) GrantDesktop.checkWindowsPrivacyStatus(grant).name
                                                else manager.checkStatus(grant).name
                                            }
                                        } finally { busy = false }
                                    }
                                }) { Text("Refresh status") }
                                if (!windows && GrantDesktop.isSupported(grant)) {
                                    Button(enabled = !busy, onClick = {
                                        scope.launch {
                                            busy = true
                                            try { status = manager.request(grant).name }
                                            finally { busy = false }
                                        }
                                    }) { Text("Request access") }
                                }
                                Button(onClick = {
                                    scope.launch {
                                        val opened = withContext(Dispatchers.IO) { GrantDesktop.openSettings(grant) }
                                        if (!opened) status = "Settings could not be opened"
                                    }
                                }) { Text("Privacy settings") }
                            }
                        }
                        Text("After changing access in Settings, return here and refresh the status.")
                    }
                }
            }
        }
    }
}
