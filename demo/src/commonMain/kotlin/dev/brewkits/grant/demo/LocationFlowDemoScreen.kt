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
@Composable
fun LocationFlowDemoScreen(
    viewModel: LocationFlowViewModel,
    modifier: Modifier = Modifier
) {
    val locationState by viewModel.locationState.collectAsState()

    Scaffold(
        topBar = {
            SmallTopAppBar(
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
            when (locationState) {
                is LocationState.Idle -> {
                    Button(
                        onClick = { viewModel.requestLocation() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Location Access")
                    }
                }

                is LocationState.Loading -> {
                    CircularProgressIndicator()
                }

                is LocationState.Ready -> {
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

                is LocationState.ShowRationale -> {
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
                                text = "We need location access to show nearby places and provide navigation features.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.requestLocation() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow Location")
                            }
                        }
                    }
                }

                is LocationState.ShowSettingsPrompt -> {
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
                                text = "Please enable location permission in Settings to use this feature.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.openAppSettings() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Settings")
                            }
                        }
                    }
                }

                is LocationState.ShowGpsPrompt -> {
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
                                text = "Location permission is granted, but GPS is turned off. Please enable GPS to continue.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.openLocationSettings() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable GPS")
                            }
                        }
                    }
                }

                is LocationState.Error -> {
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
                                text = "Error",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = locationState.message,
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
private fun LocationStatusCard(state: LocationState) {
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
                Text("State:")
                Text(
                    text = state::class.simpleName ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state is LocationState.Error) {
                Divider()
                Text(
                    text = "Message: ${state.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    fun requestLocation() {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading

            // Step 1: Request location permission
            val permissionStatus = grantManager.request(AppGrant.LOCATION)

            when (permissionStatus) {
                GrantStatus.DENIED -> {
                    // User denied, show rationale for next attempt
                    _locationState.value = LocationState.ShowRationale
                    return@launch
                }

                GrantStatus.DENIED_ALWAYS -> {
                    // Permanently denied, guide to Settings
                    _locationState.value = LocationState.ShowSettingsPrompt
                    return@launch
                }

                GrantStatus.NOT_DETERMINED -> {
                    // Shouldn't happen after request, but handle it
                    _locationState.value = LocationState.Error("Unexpected permission state")
                    return@launch
                }

                GrantStatus.GRANTED -> {
                    // Permission granted, continue to GPS check
                }
            }

            // Step 2: Check if GPS is enabled
            if (!serviceManager.isLocationEnabled()) {
                _locationState.value = LocationState.ShowGpsPrompt
                return@launch
            }

            // Both permission AND GPS are ready!
            _locationState.value = LocationState.Ready
        }
    }

    fun openLocationSettings() {
        serviceManager.openLocationSettings()
    }

    fun openAppSettings() {
        grantManager.openSettings()
    }

    fun reset() {
        _locationState.value = LocationState.Idle
    }
}

/**
 * State representing the location flow.
 */
sealed interface LocationState {
    object Idle : LocationState
    object Loading : LocationState
    object Ready : LocationState
    object ShowRationale : LocationState
    object ShowSettingsPrompt : LocationState
    object ShowGpsPrompt : LocationState
    data class Error(val message: String) : LocationState
}
