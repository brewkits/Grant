package dev.brewkits.grant.demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.ServiceManager
import dev.brewkits.grant.ServiceType
import dev.brewkits.grant.GrantAndServiceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Demo screen showing Location Permission + GPS Service Check pattern.
 *
 * This demonstrates how to combine Grant's permission APIs with service checking
 * to build a complete location flow.
 *
 * See docs/recipes/location-permission-with-gps-check.md for detailed explanation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationFlowDemoScreen(
    viewModel: LocationFlowViewModel,
    modifier: Modifier = Modifier
) {
    val locationState by viewModel.locationHandler.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Flow Demo") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Explanation card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Location Permission + GPS Check",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This demo shows how to request location permission AND ensure GPS is enabled using Grant's composable APIs.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Status display
            LocationStatusCard(state = locationState)

            // Action button
            if (!locationState.isVisible && !locationState.isReady) {
                Button(
                    onClick = { viewModel.requestLocation() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request Location Access")
                }
            }

            if (locationState.isReady) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎯 Location Ready!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Both permission and GPS are enabled. You can now start location tracking.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }

            if (locationState.showRationale) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Location Permission Needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = locationState.rationaleMessage ?: "We need location access.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { viewModel.locationHandler.onRationaleConfirmed() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow Location")
                        }
                        OutlinedButton(
                            onClick = { viewModel.locationHandler.onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            if (locationState.showPermissionSettings) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Permission Denied Permanently",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = locationState.permissionSettingsMessage ?: "Please enable location permission in Settings.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { viewModel.openAppSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Settings")
                        }
                        OutlinedButton(
                            onClick = { viewModel.locationHandler.onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            if (locationState.showServiceSettings) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "GPS Disabled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = locationState.serviceSettingsMessage ?: "Please enable GPS to continue.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { viewModel.openLocationSettings() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable GPS")
                        }
                        OutlinedButton(
                            onClick = { viewModel.locationHandler.onDismiss() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // Code reference
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📚 Implementation Reference",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See LocationFlowViewModel for the implementation code. Full recipe available at:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "docs/recipes/location-permission-with-gps-check.md",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationStatusCard(state: GrantAndServiceUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Is Ready:")
                Text(
                    text = state.isReady.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * ViewModel demonstrating the location permission + GPS flow.
 *
 * This shows how to compose Grant's permission APIs with service checking
 * to build a robust location access flow.
 */
class LocationFlowViewModel(
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) : ViewModel() {

    val locationHandler = dev.brewkits.grant.GrantAndServiceHandler(
        grantManager = grantManager,
        serviceManager = serviceManager,
        grant = AppGrant.LOCATION,
        serviceType = ServiceType.LOCATION_GPS,
        scope = viewModelScope
    )

    fun requestLocation() {
        locationHandler.request(
            rationaleMessage = "We need location access to show nearby places and provide navigation features.",
            permissionSettingsMessage = "Please enable location permission in Settings to use this feature.",
            serviceSettingsMessage = "Location permission is granted, but GPS is turned off. Please enable GPS to continue."
        ) {
            // Callback when ready
        }
    }

    fun openLocationSettings() {
        locationHandler.onServiceSettingsConfirmed()
    }

    fun openAppSettings() {
        locationHandler.onPermissionSettingsConfirmed()
    }

    fun reset() {
        locationHandler.onDismiss()
        locationHandler.refreshStatus()
    }
}
