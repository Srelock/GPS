package com.motorider.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.motorider.ui.dashboard.DashboardViewModel
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.DarkBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.TextPrimary
import com.motorider.ui.theme.TextSecondary
import com.motorider.service.OverlayService
import com.motorider.service.RadioPlayerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onBackClick: () -> Unit
) {
    val hapticsEnabled by viewModel.enableHapticAlerts.collectAsState()
    val useMph by viewModel.useMph.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val stations by viewModel.radioStations.collectAsState()
    val selectedStationId by viewModel.selectedStationId.collectAsState()

    val context = LocalContext.current
    var newStationName by remember { mutableStateOf("") }
    var newStationUrl by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Speed Limit Alert removed as requested

            // Haptic Feedback Setting
            SettingCard(title = "Haptic Feedback") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, null, tint = if (hapticsEnabled) NeonGreen else TextSecondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Vibration Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (hapticsEnabled) "On" else "Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticAlertsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }
            }

            // Floating Overlay + Radio
            SettingCard(title = "Floating overlay + radio") {
                val hasOverlayPermission = Settings.canDrawOverlays(context)
                val selectedStation = stations.firstOrNull { it.id == selectedStationId } ?: stations.firstOrNull()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable floating overlay",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (hasOverlayPermission) {
                                "Permission granted"
                            } else {
                                "Needs “Display over other apps” permission"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = overlayEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasOverlayPermission) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                viewModel.setOverlayEnabled(false)
                                return@Switch
                            }

                            viewModel.setOverlayEnabled(enabled)
                            val svcIntent = Intent(context, OverlayService::class.java).apply {
                                action = if (enabled) OverlayService.ACTION_START else OverlayService.ACTION_STOP
                            }
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ContextCompat.startForegroundService(context, svcIntent)
                            } else {
                                context.startService(svcIntent)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedStation != null) {
                    Text(
                        text = "Selected station: ${selectedStation.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(context, RadioPlayerService::class.java).apply {
                                    action = RadioPlayerService.ACTION_PLAY
                                    putExtra(RadioPlayerService.EXTRA_STATION_NAME, selectedStation.name)
                                    putExtra(RadioPlayerService.EXTRA_STATION_URL, selectedStation.url)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    ContextCompat.startForegroundService(context, intent)
                                } else {
                                    context.startService(intent)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Play") }
                        Button(
                            onClick = {
                                val intent = Intent(context, RadioPlayerService::class.java).apply {
                                    action = RadioPlayerService.ACTION_PAUSE
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Pause") }
                        Button(
                            onClick = {
                                val intent = Intent(context, RadioPlayerService::class.java).apply {
                                    action = RadioPlayerService.ACTION_STOP
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Radio stations",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newStationName,
                    onValueChange = { newStationName = it },
                    label = { Text("Station name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newStationUrl,
                    onValueChange = { newStationUrl = it },
                    label = { Text("Stream URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newStationName.isNotBlank() && newStationUrl.isNotBlank()) {
                            viewModel.upsertRadioStation(newStationName, newStationUrl)
                            if (selectedStationId.isNullOrBlank()) {
                                viewModel.setSelectedRadioStation(newStationUrl.trim())
                            }
                            newStationName = ""
                            newStationUrl = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add station")
                }

                if (stations.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No stations saved yet.", color = TextSecondary)
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    stations.forEach { station ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(station.name, color = TextPrimary)
                                Text(
                                    station.url,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(onClick = { viewModel.setSelectedRadioStation(station.id) }) {
                                Text(if (station.id == selectedStationId) "Selected" else "Select")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { viewModel.removeRadioStation(station.id) }) {
                                Text("Remove")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Units Setting
            SettingCard(title = "Units") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (useMph) "Miles per hour" else "Kilometers per hour",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (useMph) "Imperial (mph, mi)" else "Metric (km/h, km)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    Switch(
                        checked = useMph,
                        onCheckedChange = { viewModel.setUseMph(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonOrange,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}
