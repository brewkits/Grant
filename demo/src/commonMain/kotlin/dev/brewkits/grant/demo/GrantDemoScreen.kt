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
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.compose.GrantDialog
import dev.brewkits.grant.compose.GrantGroupDialog

/**
 * Demo screen showcasing various grant request scenarios.
 *
 * This demonstrates:
 * 1. Sequential grant requests (Camera → Microphone)
 * 2. Atomic Group grant requests (Location + Storage)
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
    val v11Result by viewModel.v11Result.collectAsState()
    val scenario5Result by viewModel.scenario5Result.collectAsState()

    // Live status for status chips
    val cameraStatus by viewModel.cameraGrant.status.collectAsState()
    val micStatus by viewModel.microphoneGrant.status.collectAsState()
    val notifStatus by viewModel.notificationGrant.status.collectAsState()
    val galleryStatus by viewModel.galleryGrant.status.collectAsState()
    val locationStatus by viewModel.locationGrant.status.collectAsState()
    val motionStatus by viewModel.motionGrant.status.collectAsState()
    val calendarStatus by viewModel.calendarGrant.status.collectAsState()

    // Handle grant dialogs using grant-compose
    GrantDialog(handler = viewModel.cameraGrant)
    GrantDialog(handler = viewModel.microphoneGrant)
    GrantGroupDialog(handler = viewModel.locationAndStorageGroup)
    GrantDialog(handler = viewModel.notificationGrant)
    GrantDialog(handler = viewModel.locationAlwaysGrant)
    // v1.1.0 new permissions
    GrantDialog(handler = viewModel.readContactsGrant)
    GrantDialog(handler = viewModel.contactsWriteGrant)
    GrantDialog(handler = viewModel.bluetoothAdvertiseGrant)
    GrantDialog(handler = viewModel.readCalendarGrant)
    // v1.2.0 new
    GrantDialog(handler = viewModel.motionGrant)
    GrantDialog(handler = viewModel.calendarGrant)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // OS Version Banner — auto-populates from OsInfo
        OsVersionBanner(
            platform = OsInfo.platform,
            osVersion = OsInfo.osVersion,
            apiLevel = OsInfo.apiLevel
        )

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
            result = sequentialResult,
            statusChips = listOf("Camera" to cameraStatus, "Mic" to micStatus)
        )

        HorizontalDivider()

        // Scenario 2: Atomic Group Grants
        DemoSection(
            title = "2. Atomic Group Grants",
            description = "Request Location and Storage simultaneously using GrantGroupHandler.\n\n" +
                    "OS groups these dialogs if supported. Callback only fires when ALL are granted.",
            buttonText = "Request Group (Location + Storage)",
            onClick = { viewModel.requestParallelGrants() },
            result = parallelResult,
            statusChips = listOf("Location" to locationStatus)
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
                        text = "Example: Gallery\nCan support PARTIAL_GRANTED on Android 14+ and iOS",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestGalleryGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Gallery Grant")
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
                ResultCard(
                    result = grantTypeResult,
                    statusChips = listOf(
                        "Notification" to notifStatus,
                        "Gallery" to galleryStatus
                    )
                )
            }
        }

        HorizontalDivider()

        // Scenario 4: v1.1.0 Granular Permissions
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "4. v1.1.0 — Granular Permissions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "New in v1.1.0: Separate read-only variants for least-privilege, " +
                        "CONTACTS now requests write access, BLUETOOTH_ADVERTISE for BLE peripheral mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // READ_CONTACTS
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "READ_CONTACTS — Read-only (least privilege)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android: READ_CONTACTS only\niOS: CNContactStore (same as CONTACTS)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestReadContactsGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Request READ_CONTACTS") }
                }
            }

            // CONTACTS (full)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CONTACTS — Read + Write (v1.1.0 breaking change)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android: READ_CONTACTS + WRITE_CONTACTS\niOS: CNContactStore",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestFullContactsGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Request CONTACTS (read+write)") }
                }
            }

            // BLUETOOTH_ADVERTISE
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BLUETOOTH_ADVERTISE — BLE peripheral mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android 12+: BLUETOOTH_ADVERTISE\nAndroid <12: auto-granted\niOS: CoreBluetooth",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestBluetoothAdvertiseGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Request BLUETOOTH_ADVERTISE") }
                }
            }

            // READ_CALENDAR
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "READ_CALENDAR — Read-only (least privilege)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android: READ_CALENDAR only\niOS: EKEventStore (same as CALENDAR)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.requestReadCalendarGrant() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Request READ_CALENDAR") }
                }
            }

            if (v11Result.isNotEmpty()) {
                ResultCard(result = v11Result)
            }
        }

        HorizontalDivider()

        // ==========================================
        // Scenario 5: v1.2.0 — OS-Specific Advanced
        // Automates the manual verification checklist:
        // - Android API 21: STORAGE legacy
        // - Android API 33: NOTIFICATION, granular GALLERY
        // - Android API 35: GALLERY partial access
        // - iOS Simulator: all permissions requestable
        // - iOS 17+: Calendar writeOnly → PARTIAL_GRANTED
        // ==========================================
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "5. v1.2.0 — OS-Version Advanced",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tests OS-specific permission behaviors automatically detected for your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Current OS context banner
            OsVersionBanner(
                platform = OsInfo.platform,
                osVersion = OsInfo.osVersion,
                apiLevel = OsInfo.apiLevel
            )

            // 5a: MOTION
            AdvancedPermissionCard(
                title = "MOTION — Activity Recognition",
                platformNote = OsInfo.motionBehaviorNote(),
                buttonText = "Request MOTION",
                currentStatus = motionStatus,
                onClick = { viewModel.requestMotionGrant() }
            )

            // 5b: CALENDAR full + iOS 17+ writeOnly
            AdvancedPermissionCard(
                title = "CALENDAR — Full Access (iOS 17+ aware)",
                platformNote = "iOS 17+: Full Access vs Add Only → PARTIAL_GRANTED\nAndroid: READ_CALENDAR + WRITE_CALENDAR",
                buttonText = "Request CALENDAR",
                currentStatus = calendarStatus,
                onClick = { viewModel.requestCalendarFullGrant() }
            )

            // 5c: GALLERY partial
            AdvancedPermissionCard(
                title = "GALLERY — Partial Access Detection",
                platformNote = OsInfo.galleryBehaviorNote(),
                buttonText = "Request GALLERY",
                currentStatus = galleryStatus,
                onClick = { viewModel.requestGalleryPartialGrant() }
            )

            // 5d: NOTIFICATION with API awareness
            AdvancedPermissionCard(
                title = "NOTIFICATION — API 33+ Awareness",
                platformNote = OsInfo.notificationBehaviorNote(),
                buttonText = "Request NOTIFICATION",
                currentStatus = notifStatus,
                onClick = { viewModel.requestNotificationScenario5() }
            )

            if (scenario5Result.isNotEmpty()) {
                ResultCard(
                    result = scenario5Result,
                    statusChips = listOf(
                        "Motion" to motionStatus,
                        "Calendar" to calendarStatus,
                        "Gallery" to galleryStatus
                    )
                )
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
    statusChips: List<Pair<String, GrantStatus?>> = emptyList(),
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

        if (statusChips.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                statusChips.forEach { (label, status) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusChip(status = status)
                }
            }
        }

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }

        if (result.isNotEmpty()) {
            ResultCard(result = result)
        }
    }
}

@Composable
private fun ResultCard(
    result: String,
    statusChips: List<Pair<String, GrantStatus?>> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = result, style = MaterialTheme.typography.bodyMedium)
            if (statusChips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusChips.forEach { (label, status) ->
                        Text(
                            text = "$label:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusChip(status = status)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedPermissionCard(
    title: String,
    platformNote: String,
    buttonText: String,
    currentStatus: GrantStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
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
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status = currentStatus)
            }
            Text(
                text = platformNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) { Text(buttonText) }
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
