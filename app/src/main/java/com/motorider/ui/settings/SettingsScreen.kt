package com.motorider.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.motorider.data.repository.SettingsRepository
import com.motorider.ui.dashboard.DashboardViewModel
import com.motorider.ui.theme.CardBackground
import com.motorider.ui.theme.DarkBackground
import com.motorider.ui.theme.NeonCyan
import com.motorider.ui.theme.NeonGreen
import com.motorider.ui.theme.NeonOrange
import com.motorider.ui.theme.NeonRed
import com.motorider.ui.theme.NeonPurple
import com.motorider.ui.theme.TextPrimary
import com.motorider.ui.theme.TextSecondary
import com.motorider.service.OverlayService
import com.motorider.service.RadioPlayerService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Default route name when recording starts (local date & time). */
private val routeRecordingNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")

private fun defaultRouteRecordingName(): String =
    LocalDateTime.now().format(routeRecordingNameFormatter)

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

    // HUD mode
    val hudMode by viewModel.hudMode.collectAsState()

    // Speed cameras
    val camerasEnabled by viewModel.speedCamerasEnabled.collectAsState()

    // Routes
    val routesJson by viewModel.routesJson.collectAsState()
    val activeRouteId by viewModel.activeRouteId.collectAsState()
    val isRecording by viewModel.isRecordingRoute.collectAsState()

    val context = LocalContext.current
    var newStationName by remember { mutableStateOf("") }
    var newStationUrl by remember { mutableStateOf("") }
    var newRouteName by remember { mutableStateOf("") }

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
            // ===== HUD LAYOUT MODE =====
            SettingCard(title = "HUD Layout") {
                Text(
                    text = "Choose what shows on the floating bubble",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsRepository.HudMode.entries.forEach { mode ->
                        val isSelected = hudMode == mode
                        val borderColor = if (isSelected) NeonCyan else TextSecondary.copy(alpha = 0.3f)
                        val bgColor = if (isSelected) NeonCyan.copy(alpha = 0.1f) else CardBackground

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable { viewModel.setHudMode(mode) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonCyan else TextPrimary
                                )
                                Text(
                                    text = when (mode) {
                                        SettingsRepository.HudMode.SPEED_ONLY -> "Just speed – minimal distraction"
                                        SettingsRepository.HudMode.SPEED_RADIO -> "Speed + radio controls"
                                        SettingsRepository.HudMode.SPEED_ALERTS -> "Speed + camera warnings"
                                        SettingsRepository.HudMode.FULL_HUD -> "Everything: speed, radio, cameras"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            if (isSelected) {
                                Text("✓", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            // ===== SPEED CAMERAS =====
            SettingCard(title = "Speed Camera Alerts") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Camera warnings",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Alerts at 500m (visual) and 300m (pulse)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = camerasEnabled,
                        onCheckedChange = { viewModel.setSpeedCamerasEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonRed,
                            checkedTrackColor = CardBackground,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CardBackground
                        )
                    )
                }
            }

            // ===== FAVOURITE ROUTES =====
            SettingCard(title = "Favourite Routes") {
                Text(
                    text = "Save your commute for instant camera alerts",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Recording controls
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newRouteName,
                            onValueChange = { newRouteName = it },
                            label = { Text("Route name") },
                            supportingText = {
                                Text("Filled with date & time — edit if you like", color = TextSecondary)
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val name = newRouteName.trim().ifBlank { defaultRouteRecordingName() }
                                val saved = viewModel.saveRouteRecording(name)
                                if (saved) {
                                    newRouteName = ""
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Need at least two GPS points (ride ~100 m).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) { Text("Save", color = DarkBackground) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.cancelRouteRecording()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel Recording", color = TextPrimary) }
                } else {
                    Button(
                        onClick = {
                            newRouteName = defaultRouteRecordingName()
                            viewModel.startRouteRecording()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                    ) { Text("🔴 Start Recording Route", color = DarkBackground) }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Saved routes list
                val routes = try {
                    val arr = org.json.JSONArray(routesJson)
                    buildList {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val wpCount = obj.optJSONArray("waypoints")?.length() ?: 0
                            add(Triple(
                                obj.optString("id", ""),
                                obj.optString("name", "?"),
                                wpCount
                            ))
                        }
                    }
                } catch (_: Exception) { emptyList() }

                if (routes.isEmpty()) {
                    Text(
                        text = "No routes saved yet.\nStart a recording while riding your commute.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "Saved routes (${routes.size}/5)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (routesJson.isBlank() || routesJson == "[]") {
                                Toast.makeText(context, "No routes to export", Toast.LENGTH_SHORT).show()
                            } else {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "MotoRider routes.json")
                                    putExtra(Intent.EXTRA_TEXT, routesJson)
                                }
                                context.startActivity(
                                    Intent.createChooser(send, "Export for RideMapper")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share routes for RideMapper", color = NeonCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    routes.forEach { (id, name, wpCount) ->
                        val isActive = activeRouteId == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) NeonCyan.copy(alpha = 0.1f) else CardBackground)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$name · $wpCount pts",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isActive) {
                                    Text("Active – cameras pre-loaded", color = NeonCyan,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.setActiveRouteId(if (isActive) null else id)
                                    }
                                ) { Text(if (isActive) "Deselect" else "Set Active") }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // ===== HAPTIC FEEDBACK =====
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

            // ===== FLOATING OVERLAY + RADIO =====
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
                                "Needs \u201cDisplay over other apps\u201d permission"
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

            // ===== UNITS =====
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
