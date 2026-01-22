package dev.brewkits.grant.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brewkits.grant.AppGrant

/**
 * Simplified demo screen showing all available grants.
 */
@Composable
fun SimpleGrantDemoScreen(
    viewModel: GrantDemoViewModel,
    modifier: Modifier = Modifier
) {
    // Handle grant dialogs for all grants
    GrantDialogHandler(handler = viewModel.cameraGrant)
    GrantDialogHandler(handler = viewModel.microphoneGrant)
    GrantDialogHandler(handler = viewModel.locationGrant)
    GrantDialogHandler(handler = viewModel.storageGrant)
    GrantDialogHandler(handler = viewModel.notificationGrant)
    GrantDialogHandler(handler = viewModel.contactsGrant)
    GrantDialogHandler(handler = viewModel.locationAlwaysGrant)
    GrantDialogHandler(handler = viewModel.bluetoothGrant)
    GrantDialogHandler(handler = viewModel.galleryGrant)
    GrantDialogHandler(handler = viewModel.motionGrant)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "KMP Grant Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Test all grant types with real platform implementations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Platform Differences Info Card
        PlatformDifferencesCard()

        HorizontalDivider()

        // Demo Scenarios Section
        Text(
            text = "Demo Scenarios",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Sequential Grants Demo
        val sequentialResult by viewModel.sequentialResult.collectAsState()
        DemoScenarioCard(
            title = "1. Sequential Grants",
            description = "Request Camera first, then Microphone after it's granted.",
            buttonText = "Request Camera → Microphone",
            result = sequentialResult,
            onClick = { viewModel.requestSequentialGrants() }
        )

        // Parallel Grants Demo
        val parallelResult by viewModel.parallelResult.collectAsState()
        DemoScenarioCard(
            title = "2. Parallel Grants",
            description = "Request Location and Storage simultaneously.",
            buttonText = "Request Location + Storage",
            result = parallelResult,
            onClick = { viewModel.requestParallelGrants() }
        )

        // Grant Types Demo
        val grantTypeResult by viewModel.grantTypeResult.collectAsState()
        DemoScenarioCard(
            title = "3. Test Grant Denial Flow",
            description = "Request Contacts grant. Deny it twice to see rationale → settings guide flow.",
            buttonText = "Request Contacts (Test Denial)",
            result = grantTypeResult,
            onClick = { viewModel.requestDangerousGrant() }
        )

        HorizontalDivider()

        // All Grants List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "All Grants",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Test each grant individually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { viewModel.refreshAllGrants() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("🔄 Refresh Status")
            }
        }

        // Camera
        GrantCard(
            title = "Camera",
            description = "Photo/video capture, QR scanning",
            icon = "📷",
            handler = viewModel.cameraGrant
        )

        // Microphone
        GrantCard(
            title = "Microphone",
            description = "Audio recording, voice notes",
            icon = "🎤",
            handler = viewModel.microphoneGrant
        )

        // Location
        GrantCard(
            title = "Location",
            description = "Maps, location-based services",
            icon = "📍",
            handler = viewModel.locationGrant
        )

        // Location Always
        GrantCard(
            title = "Location Always",
            description = "Background location tracking",
            icon = "🌍",
            handler = viewModel.locationAlwaysGrant
        )

        // Storage
        GrantCard(
            title = "Storage",
            description = "Read/write files",
            icon = "💾",
            handler = viewModel.storageGrant
        )

        // Gallery
        GrantCard(
            title = "Gallery",
            description = "Access photo library",
            icon = "🖼️",
            handler = viewModel.galleryGrant
        )

        // Contacts
        GrantCard(
            title = "Contacts",
            description = "Read contact list",
            icon = "👥",
            handler = viewModel.contactsGrant
        )

        // Notifications
        GrantCard(
            title = "Notifications",
            description = "Push notifications",
            icon = "🔔",
            handler = viewModel.notificationGrant
        )

        // Bluetooth
        GrantCard(
            title = "Bluetooth",
            description = "Bluetooth connectivity",
            icon = "📡",
            handler = viewModel.bluetoothGrant
        )

        GrantCard(
            title = "Motion & Fitness",
            description = "Step counting, activity recognition",
            icon = "🏃",
            handler = viewModel.motionGrant
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PlatformDifferencesCard(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📱 iOS vs 🤖 Android Differences",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show Less ▲" else "Show More ▼")
                }
            }

            if (expanded) {
                HorizontalDivider()

                // Android Section
                Text(
                    text = "🤖 Android",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "• Can request multiple times\n" +
                            "• First time: System dialog\n" +
                            "• After 1st deny: Can show rationale, request again\n" +
                            "• After 2nd deny: Must guide to Settings\n" +
                            "• shouldShowRequestPermissionRationale() helps detect state",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // iOS Section
                Text(
                    text = "📱 iOS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "• Can request ONLY ONCE ⚠️\n" +
                            "• Must explain BEFORE requesting\n" +
                            "• After deny: Cannot request again\n" +
                            "• Must immediately guide to Settings\n" +
                            "• States: notDetermined, authorized, denied, restricted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Best Practice
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💡 Best Practice",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Always show context/rationale BEFORE requesting grants on both platforms. This improves grant rates!",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Text(
                    text = "Tap 'Show More' to see how iOS and Android handle grants differently",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DemoScenarioCard(
    title: String,
    description: String,
    buttonText: String,
    result: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GrantCard(
    title: String,
    description: String,
    icon: String,
    handler: dev.brewkits.grant.GrantHandler,
    modifier: Modifier = Modifier
) {
    val GrantStatus by handler.status.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (GrantStatus) {
                dev.brewkits.grant.GrantStatus.GRANTED -> MaterialTheme.colorScheme.primaryContainer
                dev.brewkits.grant.GrantStatus.DENIED_ALWAYS -> MaterialTheme.colorScheme.errorContainer
                dev.brewkits.grant.GrantStatus.DENIED,
                dev.brewkits.grant.GrantStatus.NOT_DETERMINED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineMedium
                )

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Status badge
                    Text(
                        text = when (GrantStatus) {
                            dev.brewkits.grant.GrantStatus.GRANTED -> "✓ Granted"
                            dev.brewkits.grant.GrantStatus.DENIED -> "✗ Denied"
                            dev.brewkits.grant.GrantStatus.DENIED_ALWAYS -> "⚠️ Denied Always"
                            dev.brewkits.grant.GrantStatus.NOT_DETERMINED -> "? Not Determined"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (GrantStatus) {
                            dev.brewkits.grant.GrantStatus.GRANTED -> MaterialTheme.colorScheme.primary
                            dev.brewkits.grant.GrantStatus.DENIED_ALWAYS -> MaterialTheme.colorScheme.error
                            dev.brewkits.grant.GrantStatus.DENIED,
                            dev.brewkits.grant.GrantStatus.NOT_DETERMINED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Button(
                onClick = {
                    handler.request(
                        rationaleMessage = "$title is required for this feature.",
                        settingsMessage = "$title access is disabled. Enable it in Settings."
                    ) {
                        // Success callback
                    }
                },
                enabled = GrantStatus != dev.brewkits.grant.GrantStatus.GRANTED
            ) {
                Text(if (GrantStatus == dev.brewkits.grant.GrantStatus.GRANTED) "Granted" else "Request")
            }
        }
    }
}
