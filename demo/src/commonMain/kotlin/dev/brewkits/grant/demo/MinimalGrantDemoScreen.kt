package dev.brewkits.grant.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brewkits.grant.compose.GrantDialog

/**
 * Minimal demo screen showcasing how simple it is to use grant-compose.
 *
 * This screen demonstrates the core value proposition:
 * - Just add GrantDialog(handler) - one line of code
 * - Everything else is handled automatically
 * - Clean, readable, minimal boilerplate
 */
@Composable
fun MinimalGrantDemoScreen(
    viewModel: GrantDemoViewModel,
    modifier: Modifier = Modifier
) {
    // ✅ One line per permission - handles ALL dialog states automatically
    GrantDialog(handler = viewModel.cameraGrant)
    GrantDialog(handler = viewModel.locationGrant)

    Scaffold { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Grant Compose Demo",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "See how simple permission handling can be with grant-compose",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Camera Permission
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📷 Camera Permission",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Click the button to request camera permission. " +
                                "The library handles rationale and settings dialogs automatically.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            viewModel.cameraGrant.request(
                                rationaleMessage = "We need camera access to take photos.",
                                settingsMessage = "Camera is disabled. Please enable it in Settings."
                            ) {
                                // ✅ This only runs when permission is GRANTED
                                println("✅ Camera granted! Opening camera...")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Camera")
                    }
                }
            }

            // Location Permission
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📍 Location Permission",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Try denying this permission to see the rationale flow. " +
                                "Deny it twice to see the settings guide.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            viewModel.locationGrant.request(
                                rationaleMessage = "We need your location to show nearby places.",
                                settingsMessage = "Location is disabled. Please enable it in Settings to use this feature."
                            ) {
                                // ✅ This only runs when permission is GRANTED
                                println("✅ Location granted! Loading map...")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Location")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Code Example
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💡 The Code Behind This Screen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = """
                            // 1. Add GrantDialog (one line)
                            GrantDialog(handler = viewModel.cameraGrant)

                            // 2. Request permission
                            Button(onClick = {
                                viewModel.cameraGrant.request {
                                    // Runs only when granted
                                    openCamera()
                                }
                            })

                            That's it! No manual state handling needed.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
