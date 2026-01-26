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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.compose.GrantDialog
import kotlinx.coroutines.launch

/**
 * Simplified demo screen showing all available grants.
 */
@Composable
fun SimpleGrantDemoScreen(
    viewModel: GrantDemoViewModel,
    modifier: Modifier = Modifier
) {
    // Snackbar state for success messages
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Helper function to show success message
    fun showSuccess(grantName: String) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = "✓ $grantName granted successfully!",
                duration = SnackbarDuration.Short
            )
        }
    }

    // Handle grant dialogs for all grants using grant-compose
    GrantDialog(handler = viewModel.cameraGrant)
    GrantDialog(handler = viewModel.microphoneGrant)
    GrantDialog(handler = viewModel.locationGrant)
    GrantDialog(handler = viewModel.storageGrant)
    GrantDialog(handler = viewModel.notificationGrant)
    GrantDialog(handler = viewModel.contactsGrant)
    GrantDialog(handler = viewModel.locationAlwaysGrant)
    GrantDialog(handler = viewModel.bluetoothGrant)
    GrantDialog(handler = viewModel.galleryGrant)
    GrantDialog(handler = viewModel.motionGrant)

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Header with better styling
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔐 KMP Grant Demo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Text(
                    text = "Test all permission types with real platform implementations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

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
            handler = viewModel.cameraGrant,
            onSuccess = ::showSuccess
        )

        // Microphone
        GrantCard(
            title = "Microphone",
            description = "Audio recording, voice notes",
            icon = "🎤",
            handler = viewModel.microphoneGrant,
            onSuccess = ::showSuccess
        )

        // Location
        GrantCard(
            title = "Location",
            description = "Maps, location-based services",
            icon = "📍",
            handler = viewModel.locationGrant,
            onSuccess = ::showSuccess
        )

        // Location Always
        GrantCard(
            title = "Location Always",
            description = "Background location tracking",
            icon = "🌍",
            handler = viewModel.locationAlwaysGrant,
            onSuccess = ::showSuccess
        )

        // Storage
        GrantCard(
            title = "Storage",
            description = "Read/write files",
            icon = "💾",
            handler = viewModel.storageGrant,
            onSuccess = ::showSuccess
        )

        // Gallery
        GrantCard(
            title = "Gallery",
            description = "Access photo library",
            icon = "🖼️",
            handler = viewModel.galleryGrant,
            onSuccess = ::showSuccess
        )

        // Contacts
        GrantCard(
            title = "Contacts",
            description = "Read contact list",
            icon = "👥",
            handler = viewModel.contactsGrant,
            onSuccess = ::showSuccess
        )

        // Notifications
        GrantCard(
            title = "Notifications",
            description = "Push notifications",
            icon = "🔔",
            handler = viewModel.notificationGrant,
            onSuccess = ::showSuccess
        )

        // Bluetooth
        GrantCard(
            title = "Bluetooth",
            description = "Bluetooth connectivity",
            icon = "📡",
            handler = viewModel.bluetoothGrant,
            onSuccess = ::showSuccess
        )

        GrantCard(
            title = "Motion & Fitness",
            description = "Step counting, activity recognition",
            icon = "🏃",
            handler = viewModel.motionGrant,
            onSuccess = ::showSuccess
        )

            Spacer(modifier = Modifier.height(16.dp))
        }
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(buttonText, fontWeight = FontWeight.Medium)
            }

            if (result.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = "Result: $result",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
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
    onSuccess: (String) -> Unit,
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
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                    // Status badge with background
                    Surface(
                        color = when (GrantStatus) {
                            dev.brewkits.grant.GrantStatus.GRANTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            dev.brewkits.grant.GrantStatus.DENIED_ALWAYS -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            dev.brewkits.grant.GrantStatus.DENIED -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            dev.brewkits.grant.GrantStatus.NOT_DETERMINED -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = when (GrantStatus) {
                                dev.brewkits.grant.GrantStatus.GRANTED -> "✓ Granted"
                                dev.brewkits.grant.GrantStatus.DENIED -> "✗ Denied"
                                dev.brewkits.grant.GrantStatus.DENIED_ALWAYS -> "⚠️ Denied Always"
                                dev.brewkits.grant.GrantStatus.NOT_DETERMINED -> "? Not Determined"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = when (GrantStatus) {
                                dev.brewkits.grant.GrantStatus.GRANTED -> MaterialTheme.colorScheme.primary
                                dev.brewkits.grant.GrantStatus.DENIED_ALWAYS -> MaterialTheme.colorScheme.error
                                dev.brewkits.grant.GrantStatus.DENIED -> MaterialTheme.colorScheme.tertiary
                                dev.brewkits.grant.GrantStatus.NOT_DETERMINED -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    handler.request(
                        rationaleMessage = "$title is required for this feature.",
                        settingsMessage = "$title access is disabled. Enable it in Settings."
                    ) {
                        // Success callback - show snackbar
                        onSuccess(title)
                    }
                },
                enabled = GrantStatus != dev.brewkits.grant.GrantStatus.GRANTED,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (GrantStatus == dev.brewkits.grant.GrantStatus.GRANTED)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = if (GrantStatus == dev.brewkits.grant.GrantStatus.GRANTED) "✓ Granted" else "Request",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
