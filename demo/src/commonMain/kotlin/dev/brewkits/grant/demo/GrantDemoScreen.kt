package dev.brewkits.grant.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brewkits.grant.compose.GrantDialog

/**
 * Demo screen showcasing various grant request scenarios.
 *
 * This demonstrates:
 * 1. Sequential grant requests (Camera → Microphone)
 * 2. Parallel grant requests (Location + Storage)
 * 3. Different grant types (Runtime vs Dangerous)
 *
 * Each scenario includes:
 * - Clear button to trigger the request
 * - Result display showing the grant flow
 * - Automatic dialog handling via GrantHandler
 */
@Composable
fun GrantDemoScreen(
    viewModel: GrantDemoViewModel,
    modifier: Modifier = Modifier
) {
    val sequentialResult by viewModel.sequentialResult.collectAsState()
    val parallelResult by viewModel.parallelResult.collectAsState()
    val grantTypeResult by viewModel.grantTypeResult.collectAsState()

    // Handle grant dialogs using grant-compose
    GrantDialog(handler = viewModel.cameraGrant)
    GrantDialog(handler = viewModel.microphoneGrant)
    GrantDialog(handler = viewModel.locationGrant)
    GrantDialog(handler = viewModel.storageGrant)
    GrantDialog(handler = viewModel.notificationGrant)
    GrantDialog(handler = viewModel.contactsGrant)
    GrantDialog(handler = viewModel.locationAlwaysGrant)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text(
            text = "Grant Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "This demo showcases different grant request patterns and scenarios.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Scenario 1: Sequential Grants
        DemoSection(
            title = "1. Sequential Grants",
            description = "Request Camera first, then Microphone after it's granted.\n\n" +
                    "Use case: Video recording that needs both camera and audio.",
            buttonText = "Request Camera → Microphone",
            onClick = { viewModel.requestSequentialGrants() },
            result = sequentialResult
        )

        HorizontalDivider()

        // Scenario 2: Parallel Grants
        DemoSection(
            title = "2. Parallel Grants",
            description = "Request Location and Storage simultaneously.\n\n" +
                    "Use case: Photo app that saves geotagged images.",
            buttonText = "Request Location + Storage",
            onClick = { viewModel.requestParallelGrants() },
            result = parallelResult
        )

        HorizontalDivider()

        // Scenario 3: Grant Types
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "3. Grant Types",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Different grant categories have different risk levels and behaviors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Runtime Grant
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Runtime Grant (Lower Risk)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Example: Notifications\nLess invasive to user privacy",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestRuntimeGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Notification Grant")
                    }
                }
            }

            // Dangerous Grant
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
                        text = "Dangerous Grant (High Risk)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Example: Contacts\nAccesses sensitive user data",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestDangerousGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Contacts Grant")
                    }
                }
            }

            // Most Dangerous Grant
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Most Dangerous Grant (Highest Risk)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Example: Background Location\nContinuous tracking, major privacy implications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { viewModel.requestMostDangerousGrant() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Request Background Location")
                    }
                }
            }

            // Result display for grant types
            if (grantTypeResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = grantTypeResult,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        HorizontalDivider()

        // Reset button
        OutlinedButton(
            onClick = { viewModel.resetResults() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset All Results")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DemoSection(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    result: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }

        if (result.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Simulation controls section for testing different grant scenarios
 */
@Composable
private fun SimulationControlsSection(
    currentMode: String,
    simulationInfo: String,
    onModeChange: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🧪 Simulation Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "Test different grant scenarios:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Mode buttons - Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    text = "Real",
                    isSelected = currentMode == "REAL",
                    onClick = { onModeChange("REAL") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    text = "Realistic",
                    isSelected = currentMode == "REALISTIC",
                    onClick = { onModeChange("REALISTIC") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    text = "Auto Grant",
                    isSelected = currentMode == "AUTO_GRANT",
                    onClick = { onModeChange("AUTO_GRANT") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Mode buttons - Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    text = "Soft Deny",
                    isSelected = currentMode == "ALWAYS_DENY",
                    onClick = { onModeChange("ALWAYS_DENY") },
                    modifier = Modifier.weight(1f)
                )
                ModeButton(
                    text = "Hard Deny",
                    isSelected = currentMode == "ALWAYS_DENY_PERMANENTLY",
                    onClick = { onModeChange("ALWAYS_DENY_PERMANENTLY") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Info display
            if (simulationInfo.isNotEmpty()) {
                Text(
                    text = simulationInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Reset button
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Reset Simulation")
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (isSelected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.outlinedButtonColors()
        }
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// GrantDialogHandler has been moved to grant-compose module as GrantDialog
// Import it from: dev.brewkits.grant.compose.GrantDialog
